import { useState, useRef, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import {
  generateSimulation,
  resetSimulationRun,
  getRoad,
  getDispatchSnapshot,
  postDispatchArrival,
} from '../api/simulationApi.js'
import { normaliseTaskFromBackend } from '../api/taskApi.js'
import { getRobots } from '../api/robotApi.js'
import { decodePolyline, interpolateAlongRoute } from '../utils/routeUtils.js'
import { resolveSimulationBasePosition } from '../utils/simConfigCodec.js'

// 3 simulated days sped up to be 2 mins
const SIM_DURATION_SECONDS = 259200
const DEFAULT_REAL_DURATION_SECONDS = 720
const DEFAULT_SPEED_FACTOR = SIM_DURATION_SECONDS / DEFAULT_REAL_DURATION_SECONDS

const TICK_INTERVAL_MS = 100 // 10 ticks per real second
const WS_PUSH_INTERVAL_MS = 500 // push robot position to backend twice per second
const SETTLE_TICKS = 20 // ~2s of "nothing happening" before declaring the run complete

// Backend dispatch phase → the status our UI colours by.
function phaseToStatus(phase) {
  switch (phase) {
    case 'TO_TASK_START': return 'MOVING'          // magenta
    case 'EXECUTE_TASK': return 'ASSIGNED'         // blue
    case 'TO_BASE': return 'MOVING_TO_BASE'        // green
    // Shadow leg for a broken robot being carried by a tow robot — still broken, just moving.
    case 'BEING_TOWED': return 'NEED_MAINTENANCE'
    default: return 'IDLE'                         // grey
  }
}

// convert second to clock display
function formatSimTime(simSeconds) {
  const days = Math.floor(simSeconds / 86400)
  const hours = Math.floor((simSeconds % 86400) / 3600)
  const mins = Math.floor((simSeconds % 3600) / 60)
  const secs = Math.floor(simSeconds % 60)

  if (days > 0) {
    return `D${days} ${String(hours).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
  }
  return `${String(hours).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

export function useSimulationPlayback({
  onTaskCreated,
  onRefetchAll,
}) {
  const [isRunning, setIsRunning] = useState(false)
  const [simTime, setSimTime] = useState(0)
  const [speedFactor, setSpeedFactor] = useState(DEFAULT_SPEED_FACTOR)
  const [simAlerts, setSimAlerts] = useState([])
  const [simObstacles, setSimObstacles] = useState([])
  const [robotPositionOverrides, setRobotPositionOverrides] = useState({})
  const [robotRoutesById, setRobotRoutesById] = useState({})
  const [simulationId, setSimulationId] = useState(null)
  const [activeBasePosition, setActiveBasePosition] = useState(null)

  // refs: values that change every tick but don't need to re-render
  const eventsRef = useRef([])
  const nextEventIndexRef = useRef(0)
  const simTimeRef = useRef(0)
  const speedFactorRef = useRef(DEFAULT_SPEED_FACTOR)
  const simEpochRef = useRef(null)
  const intervalRef = useRef(null)
  const alertCounterRef = useRef(0)
  const eventIdToTaskIdRef = useRef(new Map()) // eventId → real backend task id

  // Dispatch state comes from the backend; this hook animates each leg
  const robotMovementsRef = useRef(new Map())
  const robotRevisionRef = useRef(new Map())
  const lastWsPushRef = useRef(new Map())
  const inFlightArrivalsRef = useRef(0)
  const settleTicksRef = useRef(0)

  const stompClientRef = useRef(null)
  const subscriptionRef = useRef(null)
  const simulationIdRef = useRef(null)
  const isRunningRef = useRef(false)

  const updateSpeedFactor = useCallback((factor) => {
    speedFactorRef.current = factor
    setSpeedFactor(factor)
  }, [])

  function connectWebSocket() {
    const wsUrl = `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`

    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 0,
      onConnect: async () => {
        const simId = simulationIdRef.current
        subscriptionRef.current = client.subscribe(
          `/topic/simulation/${simId}/dispatch`,
          (msg) => {
            try {
              handleDispatch(JSON.parse(msg.body))
            } catch (err) {
              console.error('[Sim] Bad dispatch message:', err)
            }
          },
        )
        console.log(`[Sim] WebSocket connected + subscribed to sim ${simId} dispatches`)

        // Load dispatches created before the WebSocket subscription was ready.
        try {
          const snapshot = await getDispatchSnapshot(simId)
          snapshot.forEach(handleDispatch)
        } catch (err) {
          console.error('[Sim] Dispatch snapshot failed:', err)
        }

        if (!isRunningRef.current) {
          isRunningRef.current = true
          setIsRunning(true)
          intervalRef.current = setInterval(tick, TICK_INTERVAL_MS)
        }

        // Subscribe to repair complete notifications
        client.subscribe(
          `/topic/simulation/${simId}/repair`,
          (msg) => {
            const { robotId, status } = JSON.parse(msg.body)
            if (status === 'IDLE') {
              // repair complete
              addAlert('Repair', `Robot ${robotId} repair complete — returning to service`)

              robotRevisionRef.current.delete(robotId)
            } else if (status === 'NEED_MAINTENANCE') {
              // broke down
              addAlert('Breakdown', `Robot ${robotId} sent for repair`)
            }
            setRobotPositionOverrides(prev => ({
              ...prev,
              [robotId]: { ...prev[robotId], status }
            }))
            onRefetchAll()
          }
        )
      },
      onDisconnect: () => console.log('[Sim] WebSocket disconnected'),
      onStompError: (frame) => console.warn('[Sim] STOMP error', frame),
    })

    client.activate()
    stompClientRef.current = client
  }

  function disconnectWebSocket() {
    if (subscriptionRef.current) {
      subscriptionRef.current.unsubscribe()
      subscriptionRef.current = null
    }
    if (stompClientRef.current) {
      stompClientRef.current.deactivate()
      stompClientRef.current = null
    }
  }

  function pushRobotPosition(robotId, lat, lng) {
    const client = stompClientRef.current
    if (!client || !client.connected) return

    const now = Date.now()
    const lastPush = lastWsPushRef.current.get(robotId) ?? 0
    if (now - lastPush < WS_PUSH_INTERVAL_MS) return
    lastWsPushRef.current.set(robotId, now)

    client.publish({
      destination: `/app/robot/${robotId}/update`,
      body: JSON.stringify({ robotId, status: 'MOVING', lat, lng }),
    })
  }

  function publishObstruction(roadId) {
    const client = stompClientRef.current
    if (!client || !client.connected) return
    client.publish({
      destination: '/app/obstruction',
      body: JSON.stringify({ id: String(roadId) }),
    })
  }

  function publishRobotBreakdown(robotId) {
    const client = stompClientRef.current
    if (!client || !client.connected) {
      console.warn('[Sim] Cannot publish breakdown — WebSocket not connected')
      return
    }
    console.log(`[Sim] Publishing breakdown for robot ${robotId}`)
    client.publish({ destination: `/app/robot/${robotId}/breakdown` })
  }


  function addAlert(type, message) {
    alertCounterRef.current += 1
    const id = `SIM-ALERT-${alertCounterRef.current}`
    setSimAlerts(prev => [
      { id, time: formatSimTime(simTimeRef.current), type, message },
      ...prev,
    ])
  }

  function clearRobotRoute(robotId) {
    setRobotRoutesById(prev => {
      if (!(robotId in prev)) return prev
      const next = { ...prev }
      delete next[robotId]
      return next
    })
  }

  function setRobotRoute(robotId, taskId, phase, coordinates) {
    setRobotRoutesById(prev => ({
      ...prev,
      [robotId]: { taskId, phase, coordinates },
    }))
  }

  function handleDispatch(dispatch) {
    const robotId = dispatch.robotId
    const lastRev = robotRevisionRef.current.get(robotId) ?? 0
    if (dispatch.revision <= lastRev) return // stale / out-of-order
    robotRevisionRef.current.set(robotId, dispatch.revision)

    if (dispatch.blocked) {
      robotMovementsRef.current.delete(robotId)
      clearRobotRoute(robotId)
      addAlert('System', `Robot ${robotId} could not be routed`)
      return // hold position
    }

    if (dispatch.phase === 'TO_TASK_START' && dispatch.taskId != null) {
      addAlert('Assignment', `Robot ${robotId} assigned to task #${dispatch.taskId}`)
    }

    // IDLE (parked at base) or a legless dispatch → snap to destination, stop animating
    if (dispatch.phase === 'IDLE' || !dispatch.routeGeo) {
      robotMovementsRef.current.delete(robotId)
      clearRobotRoute(robotId)
      setRobotPositionOverrides(prev => ({
        ...prev,
        [robotId]: { latitude: dispatch.destLat, longitude: dispatch.destLng, status: 'IDLE' },
      }))
      return
    }

    const coords = decodePolyline(dispatch.routeGeo)
    if (coords.length < 2) {
      robotMovementsRef.current.delete(robotId)
      clearRobotRoute(robotId)
      setRobotPositionOverrides(prev => ({
        ...prev,
        [robotId]: { latitude: dispatch.destLat, longitude: dispatch.destLng, status: phaseToStatus(dispatch.phase) },
      }))
      return
    }

    robotMovementsRef.current.set(robotId, {
      coords,
      etaSeconds: dispatch.etaSeconds > 0 ? dispatch.etaSeconds : 1,
      startSim: simTimeRef.current,
      phase: dispatch.phase,
      revision: dispatch.revision,
    })

    setRobotRoute(robotId, dispatch.taskId, dispatch.phase, coords)

    setRobotPositionOverrides(prev => ({
      ...prev,
      [robotId]: { latitude: coords[0][0], longitude: coords[0][1], status: phaseToStatus(dispatch.phase) },
    }))
  }


  // Create the task on the backend; the backend allocates it and pushes the dispatch over WS.
  async function handleTaskCreated(event) {
    const simId = simulationIdRef.current
    const epoch = simEpochRef.current

    const startDateTime = new Date(epoch + event.simTime * 1000).toISOString().slice(0, 19)
    const completionDateTime = new Date(epoch + event.completionDeadline * 1000).toISOString().slice(0, 19)

    const dependencyIds = (event.dependencyEventIds ?? [])
      .map(eid => eventIdToTaskIdRef.current.get(eid))
      .filter(id => id != null)

    const body = {
      name: event.taskName,
      description: event.taskDescription,
      type: event.taskType,
      priority: Math.min(event.taskPriority, 3),
      startDateTime,
      completionDateTime,
      startLocationId: event.startWaypointId,
      endLocationId: event.endWaypointId,
      dependencyIds,
      simulationId: simId,
    }

    try {
      const response = await fetch('/task', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(body),
      })

      if (!response.ok) {
        console.warn(`[Sim] Task creation failed for event ${event.eventId}:`, await response.text())
        return
      }

      const rawTask = await response.json()
      eventIdToTaskIdRef.current.set(event.eventId, rawTask.id)
      onTaskCreated(normaliseTaskFromBackend(rawTask))
      addAlert('Task', `Task created: ${event.taskName}`)
      onRefetchAll()
      // Backend allocates + pushes a dispatch over WS — no client-side assignment.
    } catch (err) {
      console.error('[Sim] Task creation error:', err)
    }
  }

  function handleRobotMalfunction(event) {
    const robotId = event.robotId
    const movement = robotMovementsRef.current.get(robotId)

    if (movement) {
      const progress = movement.etaSeconds > 0
        ? Math.max(0, Math.min((event.simTime - movement.startSim) / movement.etaSeconds, 1))
        : 1

      const pos = interpolateAlongRoute(movement.coords, progress)

      if (pos) {
        setRobotPositionOverrides(prev => ({ ...prev, [robotId]: { ...pos, status: 'ERROR' } }))

        // Send the breakdown position immediately
        const client = stompClientRef.current
        if (client && client.connected) {
          client.publish({
            destination: `/app/robot/${robotId}/update`,
            body: JSON.stringify({ robotId, status: 'ERROR', lat: pos.latitude, lng: pos.longitude }),
          })
        }
      }
    }

    // Stop the robot's current movement.
    robotMovementsRef.current.delete(robotId)
    robotRevisionRef.current.delete(robotId)
    publishRobotBreakdown(robotId)
    addAlert('Malfunction', `Robot ${robotId} malfunctioned`)
    onRefetchAll()
  }

  async function handleRouteObstruction(event) {
    const roadId = event.linkId
    if (!roadId) return

    publishObstruction(roadId)

    try {
      const road = await getRoad(roadId)
      const midLat = (road.startLat + road.endLat) / 2
      const midLon = (road.startLon + road.endLon) / 2
      const label = road.roadName || `Road ${roadId}`
      const segments = Array.isArray(road.segments) && road.segments.length > 0
        ? road.segments
        : [[[road.startLat, road.startLon], [road.endLat, road.endLon]]]
      setSimObstacles(prev => [
        ...prev,
        { id: `obs-${roadId}-${simTimeRef.current}`, latitude: midLat, longitude: midLon, label, segments },
      ])
      addAlert('Obstruction', `Road blocked: ${label}`)
    } catch (err) {
      console.error('[Sim] Obstruction handling error:', err)
      addAlert('Obstruction', `Road ${roadId} blocked`)
    }
  }


  function tickMovements(currentSimTime) {
    const completed = []

    robotMovementsRef.current.forEach((movement, robotId) => {
      const { coords, etaSeconds, startSim, phase, revision } = movement
      const progress = etaSeconds > 0 ? Math.min((currentSimTime - startSim) / etaSeconds, 1) : 1
      const pos = interpolateAlongRoute(coords, progress)
      if (!pos) return

      setRobotPositionOverrides(prev => ({ ...prev, [robotId]: { ...pos, status: phaseToStatus(phase) } }))
      pushRobotPosition(robotId, pos.latitude, pos.longitude)

      if (progress >= 1) completed.push({ robotId, revision })
    })

    completed.forEach(({ robotId, revision }) => {
      robotMovementsRef.current.delete(robotId)
      clearRobotRoute(robotId)
      postArrival(robotId, revision) // backend advances the state machine and pushes the next leg
    })
  }

  async function postArrival(robotId, revision) {
    inFlightArrivalsRef.current += 1
    try {
      await postDispatchArrival(robotId, revision)
    } catch (err) {
      console.error('[Sim] Arrival post failed:', err)
    } finally {
      inFlightArrivalsRef.current -= 1
    }
  }


  function tick() {
    if (!isRunningRef.current) return

    const advanceBy = (TICK_INTERVAL_MS / 1000) * speedFactorRef.current
    const newSimTime = simTimeRef.current + advanceBy
    simTimeRef.current = newSimTime

    const events = eventsRef.current
    while (
      nextEventIndexRef.current < events.length &&
      events[nextEventIndexRef.current].simTime <= newSimTime
    ) {
      const event = events[nextEventIndexRef.current]
      nextEventIndexRef.current += 1

      switch (event.eventType) {
        case 'TASK_CREATED': handleTaskCreated(event); break
        case 'ROBOT_MALFUNCTION': handleRobotMalfunction(event); break
        case 'ROUTE_OBSTRUCTION': handleRouteObstruction(event); break
      }
    }

    tickMovements(newSimTime)
    setSimTime(newSimTime)

    // Complete once all events fired, no legs are animating, and no arrival round-trip is pending
    // (debounced so the brief gap between an arrival POST and the next dispatch doesn't stop us early).
    const settled =
      nextEventIndexRef.current >= events.length &&
      robotMovementsRef.current.size === 0 &&
      inFlightArrivalsRef.current === 0
    if (settled) {
      settleTicksRef.current += 1
      if (settleTicksRef.current >= SETTLE_TICKS) {
        stopClock()
        addAlert('System', 'Simulation complete')
      }
    } else {
      settleTicksRef.current = 0
    }
  }

  function stopClock() {
    if (intervalRef.current) {
      clearInterval(intervalRef.current)
      intervalRef.current = null
    }
    isRunningRef.current = false
    setIsRunning(false)
  }


  const startSimulation = useCallback(async (config) => {
    try {
      const result = await generateSimulation(config)
      console.log(`[Sim] Generated simulation id=${result.simulationId ?? 'unknown'}, events=${result.events.length}`)

      eventsRef.current = result.events
      nextEventIndexRef.current = 0
      simTimeRef.current = 0
      simEpochRef.current = Date.now()
      eventIdToTaskIdRef.current = new Map()
      robotMovementsRef.current = new Map()
      robotRevisionRef.current = new Map()
      lastWsPushRef.current = new Map()
      inFlightArrivalsRef.current = 0
      settleTicksRef.current = 0
      alertCounterRef.current = 0

      setSimTime(0)
      setSimAlerts([])
      setSimObstacles([])
      setRobotPositionOverrides({})
      setRobotRoutesById({})
      setSimulationId(result.simulationId)
      setActiveBasePosition(resolveSimulationBasePosition(config))
      simulationIdRef.current = result.simulationId

      onRefetchAll()

      // Seed this run's robots at base so their dots are visible (grey) from t=0.
      try {
        const robots = await getRobots()
        const seeded = {}
        robots
          .filter(r => r.simulationId === result.simulationId)
          .forEach(r => {
            const p = r.position
            if (p && Number.isFinite(p.latitude) && Number.isFinite(p.longitude)) {
              seeded[r.id] = { ...p, status: 'IDLE' }
            }
          })
        setRobotPositionOverrides(seeded)
      } catch (err) {
        console.error('[Sim] Failed to seed robots:', err)
      }

      // Subscribe first; the clock starts inside onConnect once we're receiving dispatches.
      connectWebSocket()
    } catch (err) {
      console.error('[Sim] Failed to start simulation:', err)
      throw err
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [onRefetchAll])

  const pauseSimulation = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current)
      intervalRef.current = null
    }
    isRunningRef.current = false
    setIsRunning(false)
  }, [])

  const resumeSimulation = useCallback(() => {
    if (isRunningRef.current) return
    isRunningRef.current = true
    setIsRunning(true)
    intervalRef.current = setInterval(tick, TICK_INTERVAL_MS)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const resetSimulation = useCallback(async () => {
    stopClock()
    disconnectWebSocket()

    const simId = simulationIdRef.current
    if (simId) {
      try {
        await resetSimulationRun(simId)
      } catch (err) {
        console.error('[Sim] Reset failed:', err)
      }
    }

    eventsRef.current = []
    nextEventIndexRef.current = 0
    simTimeRef.current = 0
    simEpochRef.current = null
    eventIdToTaskIdRef.current = new Map()
    robotMovementsRef.current = new Map()
    robotRevisionRef.current = new Map()
    lastWsPushRef.current = new Map()
    inFlightArrivalsRef.current = 0
    settleTicksRef.current = 0
    alertCounterRef.current = 0
    simulationIdRef.current = null

    setSimTime(0)
    setSimAlerts([])
    setSimObstacles([])
    setRobotPositionOverrides({})
    setRobotRoutesById({})
    setSimulationId(null)
    setActiveBasePosition(null)
    setIsRunning(false)

    onRefetchAll()
  }, [onRefetchAll])

  return {
    isRunning,
    simTime,
    simTimeDisplay: formatSimTime(simTime),
    speedFactor,
    setSpeedFactor: updateSpeedFactor,
    simulationId,
    activeBasePosition,
    simAlerts,
    simObstacles,
    robotPositionOverrides,
    robotRoutesById,
    startSimulation,
    pauseSimulation,
    resumeSimulation,
    resetSimulation,
  }
}

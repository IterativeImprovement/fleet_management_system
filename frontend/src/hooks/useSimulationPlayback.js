import { useState, useRef, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import { generateSimulation, resetSimulationRun, getRoad } from '../api/simulationApi.js'
import { normaliseTaskFromBackend } from '../api/taskApi.js'
import { getRobots } from '../api/robotApi.js'
import { decodePolyline } from '../utils/routeUtils.js'

// 3 simulated days sped up to be 2 mins 
const SIM_DURATION_SECONDS = 259200
const DEFAULT_REAL_DURATION_SECONDS = 120
const DEFAULT_SPEED_FACTOR = SIM_DURATION_SECONDS / DEFAULT_REAL_DURATION_SECONDS

const TICK_INTERVAL_MS = 100 // 10 ticks per real second
const WS_PUSH_INTERVAL_MS = 500 // push robot position to backend twice per second

// go through a list of [lat, lng] waypoints and returns the position at `progress` (0–1)
function interpolateAlongRoute(coords, progress) {
  if (!coords || coords.length === 0) return null
  if (coords.length === 1) return { latitude: coords[0][0], longitude: coords[0][1] }
  if (progress <= 0) return { latitude: coords[0][0], longitude: coords[0][1] }
  if (progress >= 1) return { latitude: coords[coords.length - 1][0], longitude: coords[coords.length - 1][1] }

  const segLengths = [] // lengths of segments between positions
  let totalLength = 0 // total length of robot's journey
  for (let i = 0; i < coords.length - 1; i++) {
    const dLat = coords[i + 1][0] - coords[i][0]
    const dLng = coords[i + 1][1] - coords[i][1]
    const len = Math.sqrt(dLat * dLat + dLng * dLng)
    segLengths.push(len)
    totalLength += len
  }

  const target = progress * totalLength
  let accumulated = 0

  // find the [lat, lng] after the robot has travelled target
  for (let i = 0; i < segLengths.length; i++) {
    if (accumulated + segLengths[i] >= target) {
      const t = segLengths[i] === 0 ? 0 : (target - accumulated) / segLengths[i]
      return {
        latitude: coords[i][0] + t * (coords[i + 1][0] - coords[i][0]),
        longitude: coords[i][1] + t * (coords[i + 1][1] - coords[i][1]),
      }
    }
    accumulated += segLengths[i]
  }

  const last = coords[coords.length - 1]
  return { latitude: last[0], longitude: last[1] }
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
  const [simulationId, setSimulationId] = useState(null)

  // refs: values that change every tick but don't need to re-render
  const eventsRef = useRef([]) // stores the script
  const nextEventIndexRef = useRef(0) // stores the index of next event
  const simTimeRef = useRef(0) // the sim clock time in second
  const speedFactorRef = useRef(DEFAULT_SPEED_FACTOR)
  const simEpochRef = useRef(null) // real Date.now() when sim started, for converting sim-s to real datetimes
  const intervalRef = useRef(null)
  const alertCounterRef = useRef(0)
  const eventIdToTaskIdRef = useRef(new Map()) // eventId (position) → real backend task ID
  const simRobotsRef = useRef([]) // [{ id, type }] of seeded simulation robots
  const robotMovementsRef = useRef(new Map()) // robotId → { coords, startSim, endSim, taskId }
  const lastWsPushRef = useRef(new Map()) // robotId → last push timestamp (ms)
  const stompClientRef = useRef(null)
  const simulationIdRef = useRef(null)
  const isRunningRef = useRef(false)

  // keep speed ref in sync so the tick closure always sees the latest value
  // so users can change the speed factor
  const updateSpeedFactor = useCallback((factor) => {
    speedFactorRef.current = factor
    setSpeedFactor(factor)
  }, [])

  // ── WebSocket ───────────────────────────────────────────────────────────────
  // this is to send locations of robots to the backend continuously

  function connectWebSocket() {
    const wsUrl = `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`

    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 0,
      onConnect: () => console.log('[Sim] WebSocket connected'),
      onDisconnect: () => console.log('[Sim] WebSocket disconnected'),
      onStompError: (frame) => console.warn('[Sim] STOMP error', frame),
    })

    client.activate()
    stompClientRef.current = client
  }

  function disconnectWebSocket() {
    if (stompClientRef.current) {
      stompClientRef.current.deactivate()
      stompClientRef.current = null
    }
  }

  // send location to backend
  function pushRobotPosition(robotId, status, lat, lng) {
    const client = stompClientRef.current
    if (!client || !client.connected) return

    const now = Date.now()
    const lastPush = lastWsPushRef.current.get(robotId) ?? 0
    if (now - lastPush < WS_PUSH_INTERVAL_MS) return

    lastWsPushRef.current.set(robotId, now)

    client.publish({
      destination: `/app/robot/${robotId}/update`,
      body: JSON.stringify({ robotId, status, lat, lng }),
    })
  }

  // tell backend a road is blocked over WS (fire-and-forget, mirrors pushRobotPosition)
  function publishObstruction(roadId) {
    const client = stompClientRef.current
    if (!client || !client.connected) return

    client.publish({
      destination: '/app/obstruction',
      body: JSON.stringify({ linkId: String(roadId) }),
    })
  }

  // ── Alert helper ────────────────────────────────────────────────────────────

  function addAlert(type, message) {
    alertCounterRef.current += 1
    const id = `SIM-ALERT-${alertCounterRef.current}`
    setSimAlerts(prev => [
      { id, time: formatSimTime(simTimeRef.current), type, message },
      ...prev,
    ])
  }

  // ── Event handlers ──────────────────────────────────────────────────────────

  // Find an idle simulation robot (not currently moving), preferring a matching type
  function findAvailableRobot(taskType) {
    const busy = robotMovementsRef.current
    const free = simRobotsRef.current.filter(r => !busy.has(r.id))
    if (free.length === 0) return null
    const wanted = String(taskType).toUpperCase()
    return free.find(r => String(r.type).toUpperCase() === wanted) ?? free[0]
  }

  async function handleTaskCreated(event) {
    const simId = simulationIdRef.current
    const epoch = simEpochRef.current

    const startDateTime = new Date(epoch + event.simTime * 1000).toISOString().slice(0, 19)
    const completionDateTime = new Date(epoch + event.completionDeadline * 1000).toISOString().slice(0, 19)

    // Translate dependency event id to real backend task id
    const dependencyIds = (event.dependencyEventIds ?? [])
      .map(eid => eventIdToTaskIdRef.current.get(eid))
      .filter(id => id != null)

    const body = {
      name: event.taskName,
      description: event.taskDescription,
      type: event.taskType,
      priority: Math.min(event.taskPriority, 3), // backend accepts 1–3 only
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
        const text = await response.text()
        console.warn(`[Sim] Task creation failed for event ${event.eventId}:`, text)
        return
      }

      const rawTask = await response.json()

      //  map from event position id to  real task id
      eventIdToTaskIdRef.current.set(event.eventId, rawTask.id)

      const task = normaliseTaskFromBackend(rawTask)
      onTaskCreated(task)

      // Assign an idle robot to this task and animate it along the route.
      const robot = findAvailableRobot(event.taskType) // get idle robot
      const coords = rawTask.routeGeometry ? decodePolyline(rawTask.routeGeometry) : []

      if (robot && coords.length >= 2) {
        try {
          await fetch(`/robot/${robot.id}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ taskIdsToAdd: [rawTask.id], tasksIdsToRemove: [] }),
          })
        } catch (err) {
          console.error('[Sim] Robot assignment error:', err)
        }

        // Pace the robot to arrive by its completion deadline 
        robotMovementsRef.current.set(robot.id, {
          coords,
          startSim: event.simTime,
          endSim: event.completionDeadline,
          taskId: rawTask.id,
        })

        addAlert('Task', `Robot ${robot.id} assigned to ${event.taskName}`)
        onRefetchAll()
      }
    } catch (err) {
      console.error('[Sim] Task creation error:', err)
    }
  }

  async function handleRobotMalfunction(event) {
    const robotId = event.robotId
    const movement = robotMovementsRef.current.get(robotId)

    // Compute current position from the movement trajectory before stopping it
    if (movement) {
      const duration = movement.endSim - movement.startSim
      const progress = duration > 0
        ? Math.min((simTimeRef.current - movement.startSim) / duration, 1)
        : 0
      const pos = interpolateAlongRoute(movement.coords, progress)
      if (pos) {
        pushRobotPosition(robotId, 'ERROR', pos.latitude, pos.longitude)
      }
    }

    // stop animation
    robotMovementsRef.current.delete(robotId)

    // Unlink any assigned task via PATCH /robot/{id}
    if (movement?.taskId) {
      try {
        await fetch(`/robot/${robotId}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ tasksIdsToRemove: [movement.taskId], taskIdsToAdd: [] }),
        })
      } catch (err) {
        console.error('[Sim] Robot task removal error:', err)
      }
    }

    addAlert('Malfunction', `Robot ${robotId} malfunctioned and dropped its task`)

    // Refetch to sync UI state
    onRefetchAll()
  }

  async function handleRouteObstruction(event) {
    const roadId = event.linkId
    if (!roadId) return

    publishObstruction(roadId) // tell backend the road is blocked over WS (fire-and-forget)

    try {
      const road = await getRoad(roadId)

      // add map obstacle at the midpoint of the road segment
      const midLat = (road.startLat + road.endLat) / 2
      const midLon = (road.startLon + road.endLon) / 2
      const label = road.roadName || `Road ${roadId}`

      setSimObstacles(prev => [
        ...prev,
        { id: `obs-${roadId}-${simTimeRef.current}`, latitude: midLat, longitude: midLon, label },
      ])

      addAlert('Obstruction', `Road blocked: ${label}`)
    } catch (err) {
      console.error('[Sim] Obstruction handling error:', err)
      addAlert('Obstruction', `Road ${roadId} blocked`)
    }
  }

  // ── Animation tick ──────────────────────────────────────────────────────────

  function tickMovements(currentSimTime) {
    const completedRobotIds = []

    // loop thru every moving robot
    robotMovementsRef.current.forEach((movement, robotId) => {
      const { coords, startSim, endSim, taskId } = movement
      const duration = endSim - startSim
      if (duration <= 0) return

      const progress = Math.min((currentSimTime - startSim) / duration, 1)
      const pos = interpolateAlongRoute(coords, progress)
      if (!pos) return

      // Update position on map
      setRobotPositionOverrides(prev => ({
        ...prev,
        [robotId]: pos,
      }))

      // Push to backend via WebSocket
      pushRobotPosition(robotId, 'ASSIGNED', pos.latitude, pos.longitude)

      if (progress >= 1) {
        completedRobotIds.push({ robotId, taskId, pos })
      }
    })

    completedRobotIds.forEach(({ robotId, taskId }) => {
      robotMovementsRef.current.delete(robotId)
      completeTaskAndReturnToBase(robotId, taskId)
    })
  }

  async function completeTaskAndReturnToBase(robotId, taskId) {
    try {
      // Mark task complete
      await fetch(`/task/${taskId}/complete`, { method: 'PATCH' })

      // Tell backend robot is returning to base (sets IDLE at base coordinates)
      await fetch(`/robot/${robotId}/base`, { method: 'PATCH' })

      addAlert('Complete', `Robot ${robotId} completed task and returned to base`)
      onRefetchAll()
    } catch (err) {
      console.error('[Sim] Task completion error:', err)
    }
  }

  // ── Main clock tick ─────────────────────────────────────────────────────────

  function tick() {
    if (!isRunningRef.current) return

    const advanceBy = (TICK_INTERVAL_MS / 1000) * speedFactorRef.current
    const newSimTime = simTimeRef.current + advanceBy
    simTimeRef.current = newSimTime

    // Fire all events whose time has come
    const events = eventsRef.current
    while (
      nextEventIndexRef.current < events.length &&
      events[nextEventIndexRef.current].simTime <= newSimTime
    ) {
      const event = events[nextEventIndexRef.current]
      nextEventIndexRef.current += 1

      switch (event.eventType) {
        case 'TASK_CREATED':
          handleTaskCreated(event)
          break
        case 'ROBOT_MALFUNCTION':
          handleRobotMalfunction(event)
          break
        case 'ROUTE_OBSTRUCTION':
          handleRouteObstruction(event)
          break
      }
    }

    // Animate robot movements
    tickMovements(newSimTime)

    // Update displayed clock
    setSimTime(newSimTime)

    // Stop when all events are done and all animations complete
    if (
      nextEventIndexRef.current >= events.length &&
      robotMovementsRef.current.size === 0
    ) {
      stopClock()
      addAlert('System', 'Simulation complete')
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

  // ── Public API ───────────────────────────────────────────────────────────────

  const startSimulation = useCallback(async (config) => {
    try {
      const result = await generateSimulation(config)
      console.log(`[Sim] Generated simulation id=${result.simulationId ?? 'unknown'}, events=${result.events.length}`)

      // load simulation script into memory
      eventsRef.current = result.events
      nextEventIndexRef.current = 0
      simTimeRef.current = 0
      simEpochRef.current = Date.now()
      eventIdToTaskIdRef.current = new Map()
      robotMovementsRef.current = new Map()
      lastWsPushRef.current = new Map()
      alertCounterRef.current = 0

      setSimTime(0)
      setSimAlerts([])
      setSimObstacles([])
      setRobotPositionOverrides({})
      setSimulationId(result.simulationId)
      simulationIdRef.current = result.simulationId

      // load robot and task  from backend so the 10 seeded robots appear on the map
      onRefetchAll()

      // Fetch the seeded robots so we know which ones we can assign tasks to
      try {
        const robots = await getRobots()
        simRobotsRef.current = robots.map(r => ({ id: r.id, type: r.type }))
        console.log(`[Sim] ${simRobotsRef.current.length} robots available for assignment`)
      } catch (err) {
        console.error('[Sim] Failed to load robots for assignment:', err)
        simRobotsRef.current = []
      }

      // Start WebSocket
      connectWebSocket()

      // Start clock
      isRunningRef.current = true
      setIsRunning(true)
      intervalRef.current = setInterval(tick, TICK_INTERVAL_MS)
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
    // Stop the clock
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

    // Clear all state
    eventsRef.current = []
    nextEventIndexRef.current = 0
    simTimeRef.current = 0
    simEpochRef.current = null
    eventIdToTaskIdRef.current = new Map()
    simRobotsRef.current = []
    robotMovementsRef.current = new Map()
    lastWsPushRef.current = new Map()
    alertCounterRef.current = 0
    simulationIdRef.current = null

    setSimTime(0)
    setSimAlerts([])
    setSimObstacles([])
    setRobotPositionOverrides({})
    setSimulationId(null)
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
    simAlerts,
    simObstacles,
    robotPositionOverrides,
    startSimulation,
    pauseSimulation,
    resumeSimulation,
    resetSimulation,
  }
}

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
  // FIX: the map used to draw a grey line for every task's static start→end route regardless of
  // whether a robot was actually on it, and never removed it once the task finished. This tracks
  // each robot's actual CURRENT dispatch leg (TO_TASK_START / EXECUTE_TASK / TO_BASE /
  // BEING_TOWED) instead — it's naturally ephemeral, updated on every new leg and cleared the
  // moment that leg completes, so the map only ever shows what robots are doing right now.
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

  // Backend-authoritative movement: the backend pushes each robot's current leg; we just animate.
  const robotMovementsRef = useRef(new Map()) // robotId → { coords, etaSeconds, startSim, phase, revision }
  const robotRevisionRef = useRef(new Map())  // robotId → highest dispatch revision seen (reject stale)
  const lastWsPushRef = useRef(new Map())     // robotId → last telemetry push (ms)
  const inFlightArrivalsRef = useRef(0)       // arrival POSTs awaiting a response
  const settleTicksRef = useRef(0)

  const stompClientRef = useRef(null)
  const subscriptionRef = useRef(null)
  const simulationIdRef = useRef(null)
  const isRunningRef = useRef(false)

  const updateSpeedFactor = useCallback((factor) => {
    speedFactorRef.current = factor
    setSpeedFactor(factor)
  }, [])

  // ── WebSocket ───────────────────────────────────────────────────────────────

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

        // Reconcile any dispatches that already exist, THEN start the clock — so no dispatch
        // pushed by an early task event is missed before we're subscribed.
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
                // The backend's dispatch map entry for this robot was removed when its BEING_TOWED
                // shadow leg arrived (DispatchService.onArrive's BEING_TOWED case), so the next real
                // dispatch it publishes (heading to base or its next task) restarts revision
                // numbering at 1. Reset our own tracking to match, or that dispatch is rejected as
                // "stale" against whatever revision we last saw and the robot sits at the repair
                // depot forever even though the backend correctly redispatched it.
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

  // Stream the robot's live position to the backend (position only — backend owns status).
  // The backend needs a recent position to route a mid-return diversion from.
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

  // ── Alerts ────────────────────────────────────────────────────────────────

  function addAlert(type, message) {
    alertCounterRef.current += 1
    const id = `SIM-ALERT-${alertCounterRef.current}`
    setSimAlerts(prev => [
      { id, time: formatSimTime(simTimeRef.current), type, message },
      ...prev,
    ])
  }

  // ── Dispatch handling (backend-pushed legs) ─────────────────────────────────

  // Remove a robot's "current route" overlay — called whenever it has no active leg to show
  // (blocked, idle, legless) or once its leg completes (see tickMovements).
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

    // FIX: allocation never surfaced anywhere in the UI — TO_TASK_START is only ever pushed right
    // after TaskAllocationService assigns a robot to a task (reroutes/diversions use a different,
    // currently-unused topic), so it's a reliable "just got allocated" signal.
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
    // dispatch.etaSeconds is a real-world travel duration (distance / robot speed) that lives on
    // the SAME timeline as simTime (one robot-second of travel = one simulated second) — it must
    // NOT be re-scaled by speedFactor here. currentSimTime already advances at speedFactor per
    // real second, so comparing it directly against dispatch.etaSeconds is what actually compresses
    // movement into the sped-up demo. Multiplying by speedFactor here (as this used to) cancelled
    // that compression out entirely, making every leg take its full real-world duration to animate
    // (minutes, sometimes nearly an hour) regardless of speedFactor — starving the free-robot pool
    // because no robot could ever finish a leg within a short demo run.
    robotMovementsRef.current.set(robotId, {
      coords,
      etaSeconds: dispatch.etaSeconds > 0 ? dispatch.etaSeconds : 1,
      startSim: simTimeRef.current,
      phase: dispatch.phase,
      revision: dispatch.revision,
    })
    // This leg's own polyline — replaces whatever route was shown for this robot before (a fresh
    // dispatch always supersedes the prior one, same as the position/status override below).
    setRobotRoute(robotId, dispatch.taskId, dispatch.phase, coords)
    // colour immediately at the leg's start (higher revision replaces any in-progress leg → diversion)
    setRobotPositionOverrides(prev => ({
      ...prev,
      [robotId]: { latitude: coords[0][0], longitude: coords[0][1], status: phaseToStatus(dispatch.phase) },
    }))
  }

  // ── Event handlers ──────────────────────────────────────────────────────────

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
      // 1. Use event.simTime instead of simTimeRef.current
      // Added Math.max(0, ...) to ensure it doesn't calculate negative progress if events arrive out of order
      const progress = movement.etaSeconds > 0
          ? Math.max(0, Math.min((event.simTime - movement.startSim) / movement.etaSeconds, 1))
          : 1

      const pos = interpolateAlongRoute(movement.coords, progress)

      if (pos) {
        setRobotPositionOverrides(prev => ({ ...prev, [robotId]: { ...pos, status: 'ERROR' } }))

        // 2. Force-push the exact breakdown coordinates to the backend IMMEDIATELY,
        // bypassing the 500ms throttle so the backend doesn't assume it's still at base.
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
    // FIX: this used to latch the revision to Number.MAX_SAFE_INTEGER to block any further
    // dispatch for this robot — but that's wrong now that towing exists. RobotBreakdownService
    // calls dispatchService.cancelDispatch(robotId) on the backend, which removes this robot's
    // dispatch map entry entirely, so the NEXT dispatch published for it (the BEING_TOWED shadow
    // leg mirrored from its tow robot, see DispatchService.publishTowShadow) restarts revision
    // numbering at 1. Since 1 <= MAX_SAFE_INTEGER, that shadow leg was being silently rejected as
    // "stale" — the broken robot never animated alongside its tow robot, sat frozen at the
    // breakdown spot, and only jumped (teleported, with no interpolation) once a much later
    // post-repair dispatch finally cleared a revision check. Deleting the entry instead mirrors
    // the backend's own reset and the existing post-repair IDLE unlatch below, so the fresh
    // revision-1 sequence is accepted and the tow actually animates.
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
      // FIX: a road that crosses others is split into several sub-edges at those junctions, so its
      // raw start/end line (still used below as the icon's placement + a fallback) doesn't cover
      // the whole physical road. The backend now also returns every graph sub-edge sharing this
      // linkId — draw all of them in red so the entire obstructed stretch is marked, not just a
      // straight line between the road's two original endpoints.
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

  // ── Animation tick ──────────────────────────────────────────────────────────

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
      // The completed leg's route overlay disappears now — if a next leg follows, the dispatch
      // that arrives shortly after will draw its own route.
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

  // ── Main clock tick ─────────────────────────────────────────────────────────

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

  // ── Public API ───────────────────────────────────────────────────────────────

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

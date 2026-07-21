import { useState, useRef, useEffect, useMemo, useCallback } from 'react'

import './App.css'

import { mockRobots } from './data/mockRobots'
import { mockTasks } from './data/mockTasks'

import Topbar from './components/Topbar'
import Sidebar from './components/Sidebar'
import LiveMap from './components/LiveMap'
import AlertLog from './components/AlertLog'
import AlertPopups from './components/AlertPopups'

import {
  getTasks,
  createTaskInBackend,
  deleteTaskInBackend,
} from './api/taskApi'
import {
  getRobots,
  createRobotInBackend,
  deleteRobotInBackend,
} from './api/robotApi'
import {
  getRouteGeometry,
  getColoredRouteSegments,
} from './api/routeApi'
import {
  decodePolyline,
  getTaskRouteEndpoints,
  hasValidTaskRouteEndpoints,
} from './utils/routeUtils'
import {
  canDeleteRobot,
  reconcileRobotTaskIds,
  ROBOT_DELETE_ASSIGNED_TASKS_MESSAGE,
} from './utils/robotUtils'
import { getTaskDeleteBlockReason } from './utils/taskUtils'
import { useSimulationPlayback } from './hooks/useSimulationPlayback'

function shouldUseMockData() {
  return (
    import.meta.env.VITE_USE_MOCK_DATA === 'true' ||
    new URLSearchParams(window.location.search).get('mock') === 'true'
  )
}

function createDemoRouteCoordinates(task) {
  const startLat = Number(task.startWayPoint.latitude)
  const startLng = Number(task.startWayPoint.longitude)
  const endLat = Number(task.endWayPoint.latitude)
  const endLng = Number(task.endWayPoint.longitude)

  const latDelta = endLat - startLat
  const lngDelta = endLng - startLng

  return [
    [startLat, startLng],
    [startLat + latDelta * 0.3, startLng + lngDelta * 0.2],
    [startLat + latDelta * 0.55, startLng + lngDelta * 0.55],
    [startLat + latDelta * 0.8, startLng + lngDelta * 0.75],
    [endLat, endLng],
  ]
}

function App() {
  const [useMockData] = useState(shouldUseMockData)
  const [selectedRobotId, setSelectedRobotId] = useState(null)
  const [selectedTaskId, setSelectedTaskId] = useState(null)

  const [tasks, setTasks] = useState(() => (useMockData ? mockTasks : []))
  const [robots, setRobots] = useState(() => (useMockData ? mockRobots : []))
  const [activeTab, setActiveTab] = useState('robots')
  const representedRobots = useMemo(
    () => reconcileRobotTaskIds(robots, tasks),
    [robots, tasks]
  )

  const [routesByTaskId, setRoutesByTaskId] = useState({})
  const [routeErrorsByTaskId, setRouteErrorsByTaskId] = useState({})
  const [isLoadingRoutes, setIsLoadingRoutes] = useState(false)
  const routeCacheRef = useRef({})
  const [coloredSegmentsByTaskId, setColoredSegmentsByTaskId] = useState({})
  // Tasks whose colored-route request is in flight, so we dont ask again
  const inFlightColoredRef = useRef(new Set())

  const loadRobotsFromBackend = useCallback(async () => {
    if (useMockData) return
    try {
      const backendRobots = await getRobots()
      setRobots(backendRobots)
      setSelectedRobotId(current => {
        if (!current) return null
        return backendRobots.some(r => String(r.id) === String(current)) ? current : null
      })
    } catch (error) {
      console.error('Failed to load robots from backend.', error)
    }
  }, [useMockData])

  const loadTasksFromBackend = useCallback(async () => {
    if (useMockData) return
    try {
      const backendTasks = await getTasks()
      setTasks(backendTasks)
      setSelectedTaskId(current => {
        if (!current) return null
        return backendTasks.some(t => String(t.id) === String(current)) ? current : null
      })
    } catch (error) {
      console.error('Failed to load tasks from backend.', error)
    }
  }, [useMockData])

  const simulation = useSimulationPlayback({
    onTaskCreated: useCallback((task) => {
      setTasks(prev => [...prev.filter(t => String(t.id) !== String(task.id)), task])
    }, []),
    onRefetchAll: useCallback(() => {
      loadRobotsFromBackend()
      loadTasksFromBackend()
    }, [loadRobotsFromBackend, loadTasksFromBackend]),
  })

  useEffect(() => {
    if (useMockData) return
    let cancelled = false
    getRobots()
      .then(data => { if (!cancelled) setRobots(data) })
      .catch(err => console.error('Failed to load robots.', err))
    return () => { cancelled = true }
  }, [useMockData])

  useEffect(() => {
    if (useMockData) return
    let cancelled = false
    getTasks()
      .then(data => { if (!cancelled) setTasks(data) })
      .catch(err => console.error('Failed to load tasks.', err))
    return () => { cancelled = true }
  }, [useMockData])

  useEffect(() => {
    let isCancelled = false

    async function loadRoutesForTasks() {
      const routableTasks = tasks.filter(hasValidTaskRouteEndpoints)

      if (routableTasks.length === 0) {
        if (!isCancelled) {
          setRoutesByTaskId({})
          setRouteErrorsByTaskId({})
          setIsLoadingRoutes(false)
        }

        return
      }

      setIsLoadingRoutes(true)

      const nextRoutesByTaskId = {}
      const nextErrorsByTaskId = {}

      await Promise.all(
        routableTasks.map(async task => {
          try {
            const { start, end } = getTaskRouteEndpoints(task)
            const routeKey = `${start}|${end}`

            let routeData = routeCacheRef.current[routeKey]

            if (!routeData) {
              if (task.routeGeometry) {
                const coordinates = decodePolyline(task.routeGeometry)

                if (coordinates.length < 2) {
                  throw new Error('Stored task route is invalid')
                }

                routeData = {
                  coordinates,
                  summary: task.routeDistance === null
                    ? null
                    : { total_distance: task.routeDistance },
                }
              } else if (useMockData) {
                routeData = {
                  coordinates: createDemoRouteCoordinates(task),
                  summary: null,
                }
              } else {
                const data = await getRouteGeometry(start, end)
                const coordinates = decodePolyline(data.routeGeometry)

                routeData = {
                  coordinates,
                  summary: data.routeSummary,
                }
              }

              routeCacheRef.current[routeKey] = routeData
            }

            nextRoutesByTaskId[task.id] = {
              taskId: task.id,
              coordinates: routeData.coordinates,
              summary: routeData.summary,
            }
          } catch (error) {
            nextErrorsByTaskId[task.id] = error.message
          }
        })
      )

      if (!isCancelled) {
        setRoutesByTaskId(nextRoutesByTaskId)
        setRouteErrorsByTaskId(nextErrorsByTaskId)
        setIsLoadingRoutes(false)

        console.log('Routes by task:', nextRoutesByTaskId)
        console.log('Route errors:', nextErrorsByTaskId)
      }
    }

    loadRoutesForTasks()

    return () => {
      isCancelled = true
    }
  }, [tasks, useMockData])

  useEffect(() => {
    if (useMockData) return
    if (!selectedTaskId) return

    const key = String(selectedTaskId)

    // Already have the result cached so dont refetch
    if (coloredSegmentsByTaskId[selectedTaskId]) return
    // A request for this task is already in flight dont ask agian
    if (inFlightColoredRef.current.has(key)) return

    const route = routesByTaskId[selectedTaskId]
    if (!route) return

    const task = tasks.find(t => String(t.id) === String(selectedTaskId))
    if (!task || !hasValidTaskRouteEndpoints(task)) return

    const taskId = selectedTaskId
    inFlightColoredRef.current.add(key)

    async function fetchSelectedColoredSegments() {
      try {
        const { start, end } = getTaskRouteEndpoints(task)
        const segments = await getColoredRouteSegments(start, end)

        setColoredSegmentsByTaskId(prev => ({ ...prev, [taskId]: segments }))
      } catch (error) {
        console.error(`Failed to fetch colored route for task ${taskId}:`, error)
      } finally {
        // Clear the in-flight flag so a failed fetch can retry on reselect.
        inFlightColoredRef.current.delete(key)
      }
    }

    fetchSelectedColoredSegments()
  }, [selectedTaskId, routesByTaskId, tasks, useMockData, coloredSegmentsByTaskId])

  const routeErrorCount = Object.keys(routeErrorsByTaskId).length

  function getTaskIdFromRobot(robot) {
    return robot?.taskIds?.[0] ?? null
  }

  function robotHasTask(robot, taskId) {
    return robot.taskIds.some(
      robotTaskId => String(robotTaskId) === String(taskId)
    )
  }

  function getRobotIdFromTask(task) {
    if (task?.robotId !== null && task?.robotId !== undefined) {
      return task.robotId
    }

    const assignedRobot = representedRobots.find(robot =>
      robotHasTask(robot, task?.id)
    )

    return assignedRobot?.id || null
  }

  function handleSelectRobot(robotId) {
    setSelectedRobotId(robotId)

    if (robotId) {
      const selectedRobot = representedRobots.find(
        robot => String(robot.id) === String(robotId)
      )
      const currentTaskId = getTaskIdFromRobot(selectedRobot)

      setActiveTab('robots')
      setSelectedTaskId(currentTaskId)
    }
  }

  function handleSelectTask(taskId) {
    setSelectedTaskId(taskId)

    if (taskId) {
      const selectedTask = tasks.find(task => String(task.id) === String(taskId))
      const assignedRobotId = getRobotIdFromTask(selectedTask)

      setActiveTab('tasks')
      setSelectedRobotId(assignedRobotId)
    }
  }

  async function handleAddTask(newTask) {
    if (useMockData) {
      setTasks(prevTasks => [
        ...prevTasks.filter(task => String(task.id) !== String(newTask.id)),
        newTask,
      ])
      setSelectedTaskId(newTask.id)
      setActiveTab('tasks')

      console.log('Created demo task locally:', newTask)

      return newTask
    }

    try {
      const savedTask = await createTaskInBackend(newTask)

      setTasks(prevTasks => [
        ...prevTasks.filter(task => String(task.id) !== String(savedTask.id)),
        savedTask,
      ])
      setSelectedTaskId(savedTask.id)
      setActiveTab('tasks')

      console.log('Created task in backend:', savedTask)

      return savedTask
    } catch (error) {
      console.error('Failed to create task in backend:', error)
      throw error
    }
  }

  async function handleAddRobot(newRobot) {
    if (useMockData) {
      setRobots(prevRobots => [
        ...prevRobots.filter(robot => String(robot.id) !== String(newRobot.id)),
        newRobot,
      ])
      setSelectedRobotId(newRobot.id)
      setActiveTab('robots')

      console.log('Created demo robot locally:', newRobot)

      return newRobot
    }

    const savedRobot = await createRobotInBackend(newRobot)

    setRobots(prevRobots => [
      ...prevRobots.filter(robot => String(robot.id) !== String(savedRobot.id)),
      savedRobot,
    ])
    setSelectedRobotId(savedRobot.id)
    setActiveTab('robots')

    console.log('Created robot in backend:', savedRobot)

    return savedRobot
  }

  async function handleDeleteTask(taskId) {
    const taskToDelete = tasks.find(
      task => String(task.id) === String(taskId)
    )
    const deleteBlockReason = getTaskDeleteBlockReason(taskToDelete, tasks)

    if (deleteBlockReason) {
      throw new Error(deleteBlockReason)
    }

    if (!useMockData) {
      await deleteTaskInBackend(taskId)
    }

    setTasks(prevTasks =>
      prevTasks.filter(task => String(task.id) !== String(taskId))
    )
    setSelectedTaskId(null)
    setSelectedRobotId(null)
    setActiveTab('tasks')

    console.log(
      useMockData ? 'Deleted demo task locally:' : 'Deleted task from backend:',
      taskId
    )
  }

  async function handleDeleteRobot(robotId) {
    if (useMockData) {
      const robotToDelete = representedRobots.find(
        robot => String(robot.id) === String(robotId)
      )

      if (!canDeleteRobot(robotToDelete)) {
        throw new Error(ROBOT_DELETE_ASSIGNED_TASKS_MESSAGE)
      }

      setRobots(prevRobots =>
        prevRobots.filter(robot => String(robot.id) !== String(robotId))
      )

      setSelectedRobotId(null)
      setSelectedTaskId(null)
      setActiveTab('robots')

      console.log('Deleted demo robot locally:', robotId)
      return
    }

    await deleteRobotInBackend(robotId)

    setRobots(prevRobots =>
      prevRobots.filter(robot => String(robot.id) !== String(robotId))
    )

    setTasks(prevTasks =>
      prevTasks.map(task =>
        String(task.robotId) === String(robotId)
          ? { ...task, robotId: null }
          : task
      )
    )

    setSelectedRobotId(null)
    setSelectedTaskId(null)
    setActiveTab('robots')

    console.log('Deleted robot from backend:', robotId)
  }

  // Merge animated positions from the simulation into the robots list
  const robotsWithSimPositions = useMemo(() => {
    const overrides = simulation.robotPositionOverrides
    if (Object.keys(overrides).length === 0) return representedRobots
    return representedRobots.map(robot => {
      const pos = overrides[robot.id]
      return pos ? { ...robot, position: pos } : robot
    })
  }, [representedRobots, simulation.robotPositionOverrides])

  return (
    <main className="dashboard">
      <Topbar
        simTimeDisplay={simulation.simTimeDisplay}
        isRunning={simulation.isRunning}
        simulationId={simulation.simulationId}
        speedFactor={simulation.speedFactor}
        onStart={simulation.startSimulation}
        onPause={simulation.pauseSimulation}
        onResume={simulation.resumeSimulation}
        onReset={simulation.resetSimulation}
        onSpeedChange={simulation.setSpeedFactor}
      />

      <Sidebar
        robots={robotsWithSimPositions}
        tasks={tasks}
        selectedRobotId={selectedRobotId}
        selectedTaskId={selectedTaskId}
        onSelectRobot={handleSelectRobot}
        onSelectTask={handleSelectTask}
        activeTab={activeTab}
        onChangeTab={setActiveTab}
        onAddTask={handleAddTask}
        onAddRobot={handleAddRobot}
        onDeleteTask={handleDeleteTask}
        onDeleteRobot={handleDeleteRobot}
      />

      <LiveMap
        robots={robotsWithSimPositions}
        obstacles={simulation.simObstacles}
        routesByTaskId={routesByTaskId}
        selectedTaskId={selectedTaskId}
        selectedRobotId={selectedRobotId}
        isLoadingRoutes={isLoadingRoutes}
        routeErrorCount={routeErrorCount}
        onSelectRobot={handleSelectRobot}
        onSelectTask={handleSelectTask}
        coloredSegmentsByTaskId={coloredSegmentsByTaskId}
      />

      <AlertLog alerts={simulation.simAlerts} />

      <AlertPopups alerts={simulation.simAlerts} />
    </main>
  )
}

export default App

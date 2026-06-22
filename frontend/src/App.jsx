import { useState, useRef, useEffect, useMemo } from 'react'

import './App.css'

import { mockRobots } from './data/mockRobots'
import { mockAlerts } from './data/mockAlerts'
import { mockObstacles } from './data/mockObstacle'
import { mockTasks } from './data/mockTasks'

import Topbar from './components/Topbar'
import Sidebar from './components/Sidebar'
import LiveMap from './components/LiveMap'
import AlertLog from './components/AlertLog'

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
import { getRouteGeometry } from './api/routeApi'
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

  useEffect(() => {
    if (useMockData) return

    let isCancelled = false

    async function loadRobotsFromBackend() {
      try {
        const backendRobots = await getRobots()

        if (isCancelled) return

        setRobots(backendRobots)

        setSelectedRobotId(currentRobotId => {
          if (!currentRobotId) return null

          const robotStillExists = backendRobots.some(
            robot => String(robot.id) === String(currentRobotId)
          )

          return robotStillExists ? currentRobotId : null
        })

        console.log('Loaded robots from backend:', backendRobots)
      } catch (error) {
        console.error('Failed to load robots from backend. Using mock robots.', error)
      }
    }

    loadRobotsFromBackend()

    return () => {
      isCancelled = true
    }
  }, [useMockData])

  useEffect(() => {
    if (useMockData) return

    let isCancelled = false

    async function loadTasksFromBackend() {
      try {
        const backendTasks = await getTasks()

        if (isCancelled) return

        setTasks(backendTasks)

        setSelectedTaskId(currentTaskId => {
          if (!currentTaskId) return null

          const taskStillExists = backendTasks.some(
            task => String(task.id) === String(currentTaskId)
          )

          return taskStillExists ? currentTaskId : null
        })

        console.log('Loaded tasks from backend:', backendTasks)
      } catch (error) {
        console.error('Failed to load tasks from backend.', error)
      }
    }

    loadTasksFromBackend()

    return () => {
      isCancelled = true
    }
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
              if (useMockData) {
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

  return (
    <main className="dashboard">
      <Topbar />

      <Sidebar
        robots={representedRobots}
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
        robots={representedRobots}
        obstacles={mockObstacles}
        routesByTaskId={routesByTaskId}
        selectedTaskId={selectedTaskId}
        selectedRobotId={selectedRobotId}
        isLoadingRoutes={isLoadingRoutes}
        routeErrorCount={routeErrorCount}
        onSelectRobot={handleSelectRobot}
        onSelectTask={handleSelectTask}
      />

      <AlertLog alerts={mockAlerts} />
    </main>
  )
}

export default App

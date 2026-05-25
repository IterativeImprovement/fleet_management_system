import { useState, useRef, useEffect } from 'react'

import './App.css'

import { mockRobots } from './data/mockRobots'
import { mockAlerts } from './data/mockAlerts'
import { mockObstacles } from './data/mockObstacle'
import { mockTasks } from './data/mockTasks'

import Topbar from './components/Topbar'
import Sidebar from './components/Sidebar'
import LiveMap from './components/LiveMap'
import AlertLog from './components/AlertLog'

import { getRouteGeometry } from './api/routeApi'
import {
  decodePolyline,
  getTaskRouteEndpoints,
  hasValidTaskRouteEndpoints,
} from './utils/routeUtils'

function App() {
  const [selectedRobotId, setSelectedRobotId] = useState(null)
  const [selectedTaskId, setSelectedTaskId] = useState(null)
  const [tasks, setTasks] = useState(mockTasks)
  const [robots, setRobots] = useState(mockRobots)
  const [activeTab, setActiveTab] = useState('robots')

  const [routesByTaskId, setRoutesByTaskId] = useState({})
  const [routeErrorsByTaskId, setRouteErrorsByTaskId] = useState({})
  const [isLoadingRoutes, setIsLoadingRoutes] = useState(false)
  const routeCacheRef = useRef({})

  useEffect(() => {
    let isCancelled = false

    async function loadRoutesForTasks() {
      const routableTasks = tasks.filter(hasValidTaskRouteEndpoints)

      if (routableTasks.length === 0) {
        setRoutesByTaskId({})
        setRouteErrorsByTaskId({})
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
              const data = await getRouteGeometry(start, end)
              const coordinates = decodePolyline(data.routeGeometry)

              routeData = {
                coordinates,
                summary: data.routeSummary,
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
  }, [tasks])

  const routeErrorCount = Object.keys(routeErrorsByTaskId).length

  function getTaskIdFromRobot(robot) {
    const currentTask = robot?.tasks?.[0]

    if (!currentTask) return null

    return typeof currentTask === 'object' ? currentTask.id : currentTask
  }

  function robotHasTask(robot, taskId) {
    return robot.tasks?.some(task => {
      const robotTaskId = typeof task === 'object' ? task.id : task

      return String(robotTaskId) === String(taskId)
    })
  }

  function getRobotIdFromTask(task) {
    if (task?.robot?.id) return task.robot.id

    const assignedRobot = robots.find(robot => robotHasTask(robot, task?.id))

    return assignedRobot?.id || null
  }

  function handleSelectRobot(robotId) {
    setSelectedRobotId(robotId)

    if (robotId) {
      const selectedRobot = robots.find(robot => String(robot.id) === String(robotId))
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

  function handleAddTask(newTask) {
    setTasks(prevTask => [...prevTask, newTask])
  }

  function handleAddRobot(newRobot) {
    setRobots(prev => [...prev, newRobot])
  }

  return (
    <main className='dashboard'>
      <Topbar />

      <Sidebar 
        robots={robots} 
        tasks={tasks}
        selectedRobotId={selectedRobotId}
        selectedTaskId={selectedTaskId}
        onSelectRobot={handleSelectRobot}
        onSelectTask={handleSelectTask}
        activeTab={activeTab}
        onChangeTab={setActiveTab}
        onAddTask={handleAddTask}
        onAddRobot={handleAddRobot}
      />
    
      <LiveMap
        robots={robots}
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

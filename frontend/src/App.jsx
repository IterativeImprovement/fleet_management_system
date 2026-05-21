import { useState } from 'react'

import './App.css'

import { mockRobots } from './data/mockRobots'
import { mockAlerts } from './data/mockAlerts'
import { mockObstacles } from './data/mockObstacle'
import { mockTasks } from './data/mockTasks'

import Topbar from './components/Topbar'
import Sidebar from './components/Sidebar'
import LiveMap from './components/LiveMap'
import AlertLog from './components/AlertLog'

function App() {

  const [selectedRobotId, setSelectedRobotId] = useState(null)
  const [selectedTaskId, setSelectedTaskId] = useState(null)
  const [tasks, setTasks] = useState(mockTasks)
  const [robots, setRobots] = useState(mockRobots)
  const [activeTab, setActiveTab] = useState('robots')

  function handleSelectRobot(robotId) {
    setSelectedRobotId(robotId)

    if (robotId) {
      setActiveTab('robots')
      setSelectedTaskId(null)
    }
  }

  function handleSelectTask(taskId) {
    setSelectedTaskId(taskId)

    if (taskId) {
      setActiveTab('tasks')
      setSelectedRobotId(null)
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
        selectedRobotId={selectedRobotId}
        onSelectRobot={handleSelectRobot}
      />

      <AlertLog alerts={mockAlerts} />
    </main>
  )
}

export default App

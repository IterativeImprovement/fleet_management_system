import { useState } from 'react'
import RobotList from './RobotList'
import SelectedRobotPanel from './SelectedRobotPanel'
import TaskList from './TaskList'
import SelectedTaskPanel from './SelectedTaskPanel'
import AddTaskForm from './AddTaskForm'
import AddRobotForm from './AddRobotForm'

function Sidebar({
  robots,
  selectedRobotId,
  onSelectRobot,
  tasks,
  onChangeTab,
  activeTab,
  selectedTaskId,
  onSelectTask,
  onAddTask,
  onAddRobot,
}) {
  const [isAddingTask, setIsAddingTask] = useState(false)
  const [isAddingRobot, setIsAddingRobot] = useState(false)

  const selectedRobot = robots.find(robot => robot.id === selectedRobotId)
  const selectedTask = tasks.find(task => task.id === selectedTaskId)

  function handleOpenRobotTab() {
    onChangeTab('robots')
    onSelectRobot(null)
    onSelectTask(null)
    setIsAddingRobot(false)
    setIsAddingTask(false)
  }

  function handleOpenTaskTab() {
    onChangeTab('tasks')
    onSelectRobot(null)
    onSelectTask(null)
    setIsAddingRobot(false)
    setIsAddingTask(false)
  }

  return (
    <aside className="sidebar">
      <h2>Fleet Control</h2>

      <div className="sidebar-tabs">
        <button
          className={`tab ${activeTab === 'robots' ? 'active' : ''}`}
          onClick={handleOpenRobotTab}
        >
          Robots
        </button>

        <button
          className={`tab ${activeTab === 'tasks' ? 'active' : ''}`}
          onClick={handleOpenTaskTab}
        >
          Tasks
        </button>
      </div>

      {activeTab === 'robots' ? (
        selectedRobot ? (
          <SelectedRobotPanel
            robot={selectedRobot}
            onBack={() => onSelectRobot(null)}
            onSelectTask={onSelectTask}
          />
        ) : (
          <>
            <div className="sidebar-section-header">
              <h3>Robots</h3>

              <button
                className="add-button"
                onClick={() => {
                  setIsAddingRobot(true)
                  setIsAddingTask(false)
                }}
              >
                + Add
              </button>
            </div>

            {isAddingRobot ? (
              <AddRobotForm
                onAddRobot={(newRobot) => {
                  onAddRobot(newRobot)
                  setIsAddingRobot(false)
                  onSelectRobot(newRobot.id)
                }}
                onCancel={() => setIsAddingRobot(false)}
              />
            ) : (
              <RobotList
                robots={robots}
                selectedRobotId={selectedRobotId}
                onSelectRobot={onSelectRobot}
              />
            )}
          </>
        )
      ) : selectedTask ? (
        <SelectedTaskPanel
          task={selectedTask}
          onBack={() => {
            onSelectTask(null)
            setIsAddingTask(false)
          }}
          onViewRobot={() => {
            onChangeTab('robots')
            onSelectTask(null)
            onSelectRobot(selectedTask.assignedRobotId)
          }}
        />
      ) : (
        <>
          <div className="sidebar-section-header">
            <h3>Tasks</h3>

            <button
              className="add-button"
              onClick={() => {
                setIsAddingTask(true)
                setIsAddingRobot(false)
              }}
            >
              + Add
            </button>
          </div>

          {isAddingTask ? (
            <AddTaskForm
              tasks={tasks}
              onAddTask={(newTask) => {
                onAddTask(newTask)
                setIsAddingTask(false)
                onSelectTask(newTask.id)
              }}
              onCancel={() => setIsAddingTask(false)}
            />
          ) : (
            <TaskList
              tasks={tasks}
              selectedTaskId={selectedTaskId}
              onSelectTask={onSelectTask}
            />
          )}
        </>
      )}
    </aside>
  )
}

export default Sidebar
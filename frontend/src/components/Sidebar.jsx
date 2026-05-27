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

  const selectedRobot = robots.find(
    robot => String(robot.id) === String(selectedRobotId)
  )
  const selectedTask = tasks.find(
    task => String(task.id) === String(selectedTaskId)
  )
  const selectedTaskAssignedRobot =
    selectedTask?.robot ||
    robots.find(robot =>
      robot.tasks?.some(task => {
        const robotTaskId = typeof task === 'object' ? task.id : task

        return String(robotTaskId) === String(selectedTask?.id)
      })
    )

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
            tasks={tasks}
            onBack={() => {
              onSelectRobot(null)
              onSelectTask(null)
            }}
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
                robots={robots}
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
          assignedRobot={selectedTaskAssignedRobot}
          onBack={() => {
            onSelectTask(null)
            onSelectRobot(null)
            setIsAddingTask(false)
          }}
          onViewRobot={() => {
            onChangeTab('robots')
            onSelectRobot(selectedTaskAssignedRobot?.id)
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

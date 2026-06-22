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
  onDeleteRobot,
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
    robots.find(
      robot =>
        String(robot.id) === String(selectedTask?.robotId) ||
        robot.taskIds.some(
          taskId => String(taskId) === String(selectedTask?.id)
        )
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

      <div className="sidebar-content">
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
              onDeleteRobot={onDeleteRobot}
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
                  onAddRobot={async (newRobot) => {
                    const savedRobot = await onAddRobot(newRobot)
                    setIsAddingRobot(false)
                    onSelectRobot(savedRobot.id)
                    return savedRobot
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
                onAddTask={async (newTask) => {
                  const savedTask = await onAddTask(newTask)

                  setIsAddingTask(false)
                  onSelectTask(savedTask.id)

                  return savedTask
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
      </div>
    </aside>
  )
}

export default Sidebar

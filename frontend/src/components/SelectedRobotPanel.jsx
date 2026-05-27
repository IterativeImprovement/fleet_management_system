import {
  getRobotStatusLabel,
  getRobotStatusType,
  getRobotTypeLabel,
} from '../utils/robotUtils'

function getTaskId(task) {
  return typeof task === 'object' ? task.id : task
}

function formatWaypoint(wayPoint) {
  if (!wayPoint) return null

  return `${wayPoint.latitude}, ${wayPoint.longitude}`
}

function formatTaskRoute(task) {
  const start = formatWaypoint(task?.startWayPoint)
  const end = formatWaypoint(task?.endWayPoint)

  if (!start || !end) return 'None'

  return `${start} -> ${end}`
}

function getAssignedTasks(robot, tasks) {
  const robotTaskIds = robot.tasks?.map(getTaskId) || []

  const assignedFromTaskList = tasks.filter(task => {
    const taskRobotId = task.robot?.id
    const taskIsInRobotList = robotTaskIds.some(
      taskId => String(taskId) === String(task.id)
    )

    return String(taskRobotId) === String(robot.id) || taskIsInRobotList
  })

  if (assignedFromTaskList.length > 0) return assignedFromTaskList

  return robot.tasks || []
}

function SelectedRobotPanel({ robot, tasks = [], onBack, onSelectTask }) {
  const statusType = getRobotStatusType(robot.status)
  const statusLabel = getRobotStatusLabel(robot.status)
  const assignedTasks = getAssignedTasks(robot, tasks)
  const currentTask = assignedTasks[0]

  return (
    <div className="selected-robot-panel">
      <button className="back-button" onClick={onBack}>
        ← Back to Robots
      </button>

      <h3>Selected Robot</h3>

      <div className="selected-robot-card">
        <div className="selected-robot-header">
          <div className="selected-robot-title">
            <span className={`robot-dot ${statusType}`}></span>
            <h2>{robot.name || `Robot ${robot.id}`}</h2>
          </div>

          <span className={`selected-status ${statusType}`}>
            {statusLabel}
          </span>
        </div>

        <div className="robot-detail-row">
          <span>Robot ID:</span>
          <strong>{robot.id}</strong>
        </div>

        <div className="robot-detail-row">
          <span>Type:</span>
          <strong>{getRobotTypeLabel(robot.type)}</strong>
        </div>

        <div className="robot-detail-row">
          <span>Speed:</span>
          <strong>{robot.speed} m/s</strong>
        </div>

        <div className="robot-detail-row">
          <span>Current Task:</span>

          {currentTask ? (
            <button
              type="button"
              onClick={() => onSelectTask(currentTask.id || currentTask)}
              className="assigned-task-link"
            >
              {currentTask.name || currentTask.id || currentTask}
            </button>
          ) : (
            <strong>No assigned task</strong>
          )}
        </div>

        <div className="robot-detail-row">
          <span>Assigned Tasks:</span>
          <strong>{assignedTasks.length}</strong>
        </div>

        <div className="robot-detail-row">
          <span>Current Route:</span>
          <strong>{formatTaskRoute(currentTask)}</strong>
        </div>
      </div>

      <div className="selected-actions">
        <h3>Actions</h3>
        <button className="primary-action">Send to Base</button>
        <button className="secondary-action">Send to Servicing</button>
      </div>
    </div>
  )
}

export default SelectedRobotPanel

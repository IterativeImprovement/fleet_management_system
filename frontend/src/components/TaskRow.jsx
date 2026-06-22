import {
  getPriorityType,
  getTaskStatusLabel,
  getTaskStatusType,
} from '../utils/taskUtils'

function TaskRow({ task, isSelected, onSelectTask }) {
  const statusType = getTaskStatusType(task.status)
  const statusLabel = getTaskStatusLabel(task.status)
  const assignedRobotId = task.robotId

  return (
    <div
      className={`task-row ${isSelected ? 'selected' : ''}`}
      onClick={() => onSelectTask(task.id)}
    >
      <div className="task-row-header">
        <h3>{task.name}</h3>

        <span className={`task-priority ${task.priorityType}`}>
          {getPriorityType(task.priority)}
        </span>
      </div>

      <p className="task-description">
        {task.description}
      </p>

      <p className="task-status">
        <span className={`task-status-pill ${statusType}`}>
          {statusLabel}
        </span>

        {assignedRobotId && (
          <span>
            Assigned to Robot {assignedRobotId}
          </span>
        )}
      </p>
    </div>
  )
}

export default TaskRow

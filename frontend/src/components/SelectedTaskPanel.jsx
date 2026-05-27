import {
  getPriorityType,
  getTaskStatusLabel,
  getTaskStatusType,
} from '../utils/taskUtils'

function formatWaypoint(wayPoint) {
  if (!wayPoint) return '-'
  return `${wayPoint.latitude}, ${wayPoint.longitude}`
}

function formatDateTime(dateTime) {
  if (!dateTime) return '-'
  return dateTime.replace('T', ' ')
}

function getDependencies(task) {
  return task.dependencies || task.tasks || []
}

function formatDependency(dependency) {
  if (typeof dependency === 'object') {
    return dependency.name || `Task ${dependency.id}`
  }

  return `Task ${dependency}`
}

function SelectedTaskPanel({ task, assignedRobot, onBack, onViewRobot }) {
  const statusType = getTaskStatusType(task.status)
  const dependencies = getDependencies(task)

  return (
    <div className="selected-task-panel">
      <button className="back-button" onClick={onBack}>
        ← Back to Tasks
      </button>

      <h3>Selected Task</h3>

      <div className="selected-task-card">
        <div className="selected-task-header">
          <div>
            <h2>{task.name || `Task ${task.id}`}</h2>
            <p className="selected-task-subtitle">
              {task.type || 'No task type'}
            </p>
          </div>

          <span className={`task-priority ${task.priorityType}`}>
            {getPriorityType(task.priority)}
          </span>
        </div>

        <div className="task-detail-row">
          <span>Task ID:</span>
          <strong>{task.id}</strong>
        </div>

        <div className="task-detail-row">
          <span>Status:</span>
          <strong>
            <span className={`task-status-pill ${statusType}`}>
              {getTaskStatusLabel(task.status)}
            </span>
          </strong>
        </div>

        <div className="task-detail-row">
          <span>Description:</span>
          <strong>{task.description || '-'}</strong>
        </div>

        <div className="task-detail-row">
          <span>Start Time:</span>
          <strong>{formatDateTime(task.startDateTime)}</strong>
        </div>

        <div className="task-detail-row">
          <span>Completion Time:</span>
          <strong>{formatDateTime(task.completionDateTime)}</strong>
        </div>

        <div className="task-detail-row">
          <span>Start Waypoint:</span>
          <strong>{formatWaypoint(task.startWayPoint)}</strong>
        </div>

        <div className="task-detail-row">
          <span>End Waypoint:</span>
          <strong>{formatWaypoint(task.endWayPoint)}</strong>
        </div>

        <div className="task-detail-row">
          <span>Assigned Robot:</span>
          <strong>
            {assignedRobot
              ? assignedRobot.name || `Robot ${assignedRobot.id}`
              : 'Unassigned'}
          </strong>
        </div>

        <div className="task-detail-row">
          <span>Dependencies:</span>
          <strong>
            {dependencies.length > 0
              ? dependencies.map(formatDependency).join(', ')
              : 'None'}
          </strong>
        </div>

        {assignedRobot && (
          <button
            className="primary-action"
            onClick={() => onViewRobot(assignedRobot.id)}
          >
            View Assigned Robot
          </button>
        )}
      </div>
    </div>
  )
}

export default SelectedTaskPanel

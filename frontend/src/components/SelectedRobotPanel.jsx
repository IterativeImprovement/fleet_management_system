import { getRobotStatusLabel, getRobotStatusType } from '../utils/robotUtils'

function SelectedRobotPanel({ robot, onBack, onSelectTask }) {
  const batteryDisplay =
    robot.battery === null || robot.battery === undefined
      ? 'Not available'
      : `${robot.battery}%`

  const statusType = getRobotStatusType(robot.status)
  const statusLabel = getRobotStatusLabel(robot.status)
  const currentTask = robot.tasks?.[0]

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
          <strong>{robot.type === 0 ? 'Standard' : 'Unknown'}</strong>
        </div>

        <div className="robot-detail-row">
          <span>Speed:</span>
          <strong>{robot.speed} m/s</strong>
        </div>

        <div className="robot-detail-row">
          <span>Battery:</span>
          <strong>{batteryDisplay}</strong>
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
          <strong>{robot.tasks?.length || 0}</strong>
        </div>

        <div className="robot-detail-row">
          <span>Current Route:</span>
          <strong>
            {!robot.route
              ? 'None'
              : typeof robot.route === 'string'
                ? robot.route
                : robot.route.id
                  ? `Route ${robot.route.id}`
                  : 'Assigned'}
          </strong>
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
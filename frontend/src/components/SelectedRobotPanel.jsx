function SelectedRobotPanel({ robot, onBack, onSelectTask }) {
  const batteryDisplay =
    robot.battery === null || robot.battery === undefined
      ? 'Not available'
      : `${robot.battery}%`

  const etaDisplay = robot.eta || '-'
  const locationDisplay = robot.location || 'Unknown'
  const routeDisplay = robot.route || '-'

  return (
    <div className="selected-robot-panel">
      <button className="back-button" onClick={onBack}>
        ← Back to Robots
      </button>

      <h3>Selected Robot</h3>

      <div className="selected-robot-card">
        <div className="selected-robot-header">
          <div className="selected-robot-title">
            <span className={`robot-dot ${robot.statusType}`}></span>
            <h2>{robot.id}</h2>
          </div>

          <span className={`selected-status ${robot.statusType}`}>
            {robot.status || 'Unknown'}
          </span>
        </div>

        <div className="robot-detail-row">
          <span>Battery:</span>
          <strong>{batteryDisplay}</strong>
        </div>

        <div className="robot-detail-row">
          <span>Current Task:</span>

          {robot.currentTask ? (
            <button
              type="button"
              onClick={() => onSelectTask(robot.currentTask)}
              className="assigned-task-link"
            >
              {robot.currentTask}
            </button>
          ) : (
            <strong>No assigned task</strong>
          )}
        </div>

        <div className="robot-detail-row">
          <span>ETA:</span>
          <strong>{etaDisplay}</strong>
        </div>

        <div className="robot-detail-row">
          <span>Location:</span>
          <strong>{locationDisplay}</strong>
        </div>

        <div className="robot-detail-row">
          <span>Current Route:</span>
          <strong>{routeDisplay}</strong>
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
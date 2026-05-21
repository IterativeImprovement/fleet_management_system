function SelectedTaskPanel({ task, onBack, onViewRobot }) {
  return (
    <div className="selected-task-panel">
      <button className="back-button" onClick={onBack}>
        ← Back to Tasks
      </button>

      <h3>Selected Task</h3>

      <div className="selected-task-card">
        <div className="selected-task-header">
          <h2>{task.id}</h2>

          <span className={`task-priority ${task.priorityType}`}>
            {task.priority}
          </span>
        </div>

        <div className="task-detail-row">
          <span>Status:</span>
          <strong>{task.status}</strong>
        </div>

        <div className="task-detail-row">
          <span>Start:</span>
          <strong>{task.start || '-'}</strong>
        </div>

        <div className="task-detail-row">
          <span>Destination:</span>
          <strong>{task.destination || '-'}</strong>
        </div>

        <div className="task-detail-row">
          <span>Assigned Robot:</span>
          <strong>{task.assignedRobotId || 'Unassigned'}</strong>
        </div>

        <div className="task-detail-row">
          <span>ETA:</span>
          <strong>{task.eta || '-'}</strong>
        </div>

        {task.assignedRobotId && (
          <button
            className="primary-action"
            onClick={() => onViewRobot(task.assignedRobotId)}
          >
            View Assigned Robot
          </button>
        )}
      </div>
    </div>
  )
}

export default SelectedTaskPanel
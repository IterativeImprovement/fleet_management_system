function TaskRow({ task, isSelected, onSelectTask }) {
  return (
    <div 
      className={`task-row ${isSelected ? 'selected' : ''}`}
      onClick={() => onSelectTask(task.id)}
    >
      <h3>{task.id}</h3>

      <span className={`task-priority ${task.priorityType}`}>
        {task.priority}
      </span>

      <p className="task-status">
        {task.assignedRobotId 
          ? `Assigned to ${task.assignedRobotId}` 
          : task.status}
      </p>
    </div>
  )
}

export default TaskRow
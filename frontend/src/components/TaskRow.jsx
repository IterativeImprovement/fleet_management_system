import { getPriorityType } from "../utils/taskUtils"

function TaskRow({ task, isSelected, onSelectTask }) {
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
        {task.robot
          ? `Assigned to Robot ${task.robot.id}`
          : 'Unassigned'}
      </p>
    </div>
  )
}

export default TaskRow
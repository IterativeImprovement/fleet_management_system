import TaskRow from "./TaskRow"

function TaskList({ tasks, selectedTaskId, onSelectTask }) {
  return (
    <div className="task-panel">
      {tasks.map(task => (
        <TaskRow 
          key={task.id}
          task={task}
          isSelected={task.id === selectedTaskId}
          onSelectTask={onSelectTask}
        />
      ))}
    </div>
  )
}

export default TaskList
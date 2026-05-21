import { useState } from 'react'
import { createTask } from '../utils/taskUtils'

function AddTaskForm({ tasks = [], onAddTask, onCancel }) {
  const [taskId, setTaskId] = useState('')
  const [priority, setPriority] = useState('Medium')
  const [start, setStart] = useState('')
  const [destination, setDestination] = useState('')
  const [requiredCompletionTime, setRequiredCompletionTime] = useState('')
  const [dependencyId, setDependencyId] = useState('')

  function handleSubmit(event) {
    event.preventDefault()

    const newTask = createTask({
      id: taskId,
      priority,
      start,
      destination,
      requiredCompletionTime,
      dependencies: dependencyId ? [dependencyId] : [],
    })

    onAddTask(newTask)

    setTaskId('')
    setPriority('Medium')
    setStart('')
    setDestination('')
    setRequiredCompletionTime('')
    setDependencyId('')
  }

  return (
    <form className="add-task-form" onSubmit={handleSubmit}>
      <h3>Add Task</h3>

      <label>
        Task ID
        <input
          type="text"
          value={taskId}
          onChange={event => setTaskId(event.target.value)}
          placeholder="T-104"
          required
        />
      </label>

      <label>
        Priority
        <select
          value={priority}
          onChange={event => setPriority(event.target.value)}
        >
          <option value="High">High</option>
          <option value="Medium">Medium</option>
          <option value="Low">Low</option>
        </select>
      </label>

      <label>
        Start Point
        <input
          type="text"
          value={start}
          onChange={event => setStart(event.target.value)}
          placeholder="Warehouse"
          required
        />
      </label>

      <label>
        Destination
        <input
          type="text"
          value={destination}
          onChange={event => setDestination(event.target.value)}
          placeholder="Loading Bay"
          required
        />
      </label>

      <label>
        Required Completion Time
        <input
          type="text"
          value={requiredCompletionTime}
          onChange={event => setRequiredCompletionTime(event.target.value)}
          placeholder="00:30:00"
        />
      </label>

      <label>
        Dependency
        <select
          value={dependencyId}
          onChange={event => setDependencyId(event.target.value)}
        >
          <option value="">None</option>

          {tasks.map(task => (
            <option key={task.id} value={task.id}>
              {task.id} — {task.destination || 'No destination'}
            </option>
          ))}
        </select>
      </label>

      <div className="form-actions">
        <button type="button" onClick={onCancel}>
          Cancel
        </button>

        <button type="submit">
          Add Task
        </button>
      </div>
    </form>
  )
}

export default AddTaskForm
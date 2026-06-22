import { useState } from 'react'
import { createTask } from '../utils/taskUtils'

function parseWayPoint(value) {
  const [latitude, longitude] = value.split(',').map(part => Number(part.trim()))

  return {
    id: Date.now(),
    latitude,
    longitude,
  }
}

function AddTaskForm({ tasks = [], onAddTask, onCancel }) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [type, setType] = useState('STANDARD')
  const [priority, setPriority] = useState(2)
  const [startDateTime, setStartDateTime] = useState('')
  const [completionDateTime, setCompletionDateTime] = useState('')
  const [startWayPointStr, setStartWayPointStr] = useState('')
  const [endWayPointStr, setEndWayPointStr] = useState('')
  const [dependencyId, setDependencyId] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  function isValidWayPoint(value) {
    const parts = value.split(',')

    if (parts.length !== 2) return false

    const latitude = Number(parts[0].trim())
    const longitude = Number(parts[1].trim())

    return Number.isFinite(latitude) && Number.isFinite(longitude)
  }

  function normaliseDateTime(value) {
    if (!value) return ''
    return value.length === 16 ? `${value}:00` : value
  }

  async function handleSubmit(event) {
    event.preventDefault()

    const trimmedName = name.trim()
    const trimmedDescription = description.trim()
    const trimmedType = type.trim()
    const trimmedStartWayPoint = startWayPointStr.trim()
    const trimmedEndWayPoint = endWayPointStr.trim()

    if (!trimmedName) {
      setError('Task name is required')
      return
    }

    if (!trimmedDescription) {
      setError('Description is required')
      return
    }

    if (!trimmedType) {
      setError('Task type is required')
      return
    }

    if (!startDateTime || !completionDateTime) {
      setError('Start and completion time are required')
      return
    }

    if (!isValidWayPoint(trimmedStartWayPoint)) {
      setError('Start waypoint must be in latitude,longitude format')
      return
    }

    if (!isValidWayPoint(trimmedEndWayPoint)) {
      setError('End waypoint must be in latitude,longitude format')
      return
    }

    const newTask = createTask({
      id: Date.now(),
      name: trimmedName,
      description: trimmedDescription,
      type: trimmedType,
      priority: Number(priority),
      startDateTime: normaliseDateTime(startDateTime),
      completionDateTime: normaliseDateTime(completionDateTime),
      startWayPoint: parseWayPoint(trimmedStartWayPoint),
      endWayPoint: parseWayPoint(trimmedEndWayPoint),
      robotId: null,
      dependencyIds: dependencyId ? [Number(dependencyId)] : [],
    })

    try {
      setIsSubmitting(true)
      await onAddTask(newTask)

      setName('')
      setDescription('')
      setType('STANDARD')
      setPriority(2)
      setStartDateTime('')
      setCompletionDateTime('')
      setStartWayPointStr('')
      setEndWayPointStr('')
      setDependencyId('')
      setError('')
    } catch (submitError) {
      setError(submitError.message || 'Failed to add task')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <form className="add-task-form" onSubmit={handleSubmit}>
      <h3>Add Task</h3>

      <label>
        Task Name
        <input
          type="text"
          value={name}
          onChange={event => {
            setName(event.target.value)
            setError('')
          }}
          placeholder="Tuas shipment"
          required
        />
      </label>

      <label>
        Description
        <input
          type="text"
          value={description}
          onChange={event => {
            setDescription(event.target.value)
            setError('')
          }}
          placeholder="Transport from Tuas to Harbourfront"
          required
        />
      </label>

      <label>
        Type
        <select
          value={type}
          onChange={event => {
            setType(event.target.value)
            setError('')
          }}
          required
        >
          <option value="STANDARD">Standard</option>
          <option value="LARGE">Large</option>
        </select>
      </label>

      <label>
        Priority
        <select
          value={priority}
          onChange={event => setPriority(Number(event.target.value))}
        >
          <option value={1}>High</option>
          <option value={2}>Medium</option>
          <option value={3}>Low</option>
        </select>
      </label>

      <label>
        Start Date Time
        <input
          type="datetime-local"
          value={startDateTime}
          onChange={event => {
            setStartDateTime(event.target.value)
            setError('')
          }}
          required
        />
      </label>

      <label>
        Completion Date Time
        <input
          type="datetime-local"
          value={completionDateTime}
          onChange={event => {
            setCompletionDateTime(event.target.value)
            setError('')
          }}
          required
        />
      </label>

      <label>
        Start Waypoint
        <input
          type="text"
          value={startWayPointStr}
          onChange={event => {
            setStartWayPointStr(event.target.value)
            setError('')
          }}
          placeholder="1.3081,103.8551"
          required
        />
      </label>

      <label>
        End Waypoint
        <input
          type="text"
          value={endWayPointStr}
          onChange={event => {
            setEndWayPointStr(event.target.value)
            setError('')
          }}
          placeholder="1.2739,103.8012"
          required
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
              {task.id} — {task.name || 'Unnamed task'}
            </option>
          ))}
        </select>
      </label>

      {error && <p className="form-error">{error}</p>}

      <div className="form-actions">
        <button type="button" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </button>

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Adding...' : 'Add Task'}
        </button>
      </div>
    </form>
  )
}

export default AddTaskForm

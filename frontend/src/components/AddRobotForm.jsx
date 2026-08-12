import { useState } from 'react'
import { createRobot } from '../utils/robotUtils'

function AddRobotForm({ robots = [], onAddRobot, onCancel }) {
  const [name, setName] = useState('')
  const [type, setType] = useState('STANDARD')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()

    const trimmedName = name.trim()

    if (!trimmedName) {
      setError('Robot name is required')
      return
    }

    const robotExists = robots.some(
      robot => robot.name.trim().toLowerCase() === trimmedName.toLowerCase()
    )

    if (robotExists) {
      setError('Robot name already exists')
      return
    }

    // create frontend representation of robot
    const newRobot = createRobot({
      id: Date.now(),
      name: trimmedName,
      type,
      status: 'IDLE',
      position: null,
      taskIds: [],
    })

    try {
      setIsSubmitting(true)
      await onAddRobot(newRobot)

      setName('')
      setType('STANDARD')
      setError('')
    } catch (submitError) {
      setError(submitError.message || 'Failed to add robot')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <form className="add-robot-form" onSubmit={handleSubmit}>
      <h3>Add Robot</h3>

      <label>
        Robot Name
        <input
          type="text"
          value={name}
          disabled={isSubmitting}
          onChange={event => {
            setName(event.target.value)
            setError('')
          }}
          placeholder="R-004"
          required
        />
      </label>

      <label>
        Type
        <select
          value={type}
          disabled={isSubmitting}
          onChange={event => setType(event.target.value)}
        >
          <option value="STANDARD">Standard</option>
          <option value="LARGE">Large</option>
        </select>
      </label>

      {error && <p className="form-error">{error}</p>}

      <div className="form-actions">
        <button type="button" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </button>

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Adding...' : 'Add Robot'}
        </button>
      </div>
    </form>
  )
}

export default AddRobotForm

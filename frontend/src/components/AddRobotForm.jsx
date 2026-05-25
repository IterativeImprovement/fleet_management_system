import { useState } from 'react'
import { createRobot } from '../utils/robotUtils'

function AddRobotForm({ robots = [], onAddRobot, onCancel }) {
  const [name, setName] = useState('')
  const [type, setType] = useState(0)
  const [error, setError] = useState('')

  function handleSubmit(event) {
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

    const newRobot = createRobot({
      id: Date.now(),
      name: trimmedName,
      type: Number(type),
      route: null,
      tasks: [],
      x: null,
      y: null,
      path: [],
    })

    onAddRobot(newRobot)

    setName('')
    setType(0)
    setError('')
  }

  return (
    <form className="add-robot-form" onSubmit={handleSubmit}>
      <h3>Add Robot</h3>

      <label>
        Robot Name
        <input
          type="text"
          value={name}
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
          onChange={event => setType(Number(event.target.value))}
        >
          <option value={0}>Standard</option>
        </select>
      </label>

      {error && <p className="form-error">{error}</p>}

      <div className="form-actions">
        <button type="button" onClick={onCancel}>
          Cancel
        </button>

        <button type="submit">
          Add Robot
        </button>
      </div>
    </form>
  )
}

export default AddRobotForm
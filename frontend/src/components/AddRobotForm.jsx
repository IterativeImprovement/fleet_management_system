import { useState } from 'react'
import { createRobot } from '../utils/robotUtils'

function AddRobotForm({ onAddRobot, onCancel }) {
  const [robotId, setRobotId] = useState('')

  function handleSubmit(event) {
    event.preventDefault()

    const newRobot = createRobot({
      id: robotId,
    })

    onAddRobot(newRobot)

    setRobotId('')
  }

  return (
    <form className="add-robot-form" onSubmit={handleSubmit}>
      <h3>Add Robot</h3>

      <label>
        Robot ID
        <input
          type="text"
          value={robotId}
          onChange={event => setRobotId(event.target.value)}
          placeholder="R-004"
          required
        />
      </label>

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
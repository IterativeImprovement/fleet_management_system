import {
  getRobotStatusLabel,
  getRobotStatusType,
  getRobotTypeLabel,
} from '../utils/robotUtils'

function RobotRow({ robot, isSelected, onSelectRobot }) {
  const statusType = getRobotStatusType(robot.status)
  const statusLabel = getRobotStatusLabel(robot.status)

  return (
    <div
      className={`robot-row ${isSelected ? 'selected' : ''}`}
      onClick={() => onSelectRobot(robot.id)}
    >
      <div className="robot-left">
        <span className={`robot-dot ${statusType}`}></span>

        <div className="robot-info">
          <h3>{robot.name || `Robot ${robot.id}`}</h3>
          <p>{getRobotTypeLabel(robot.type)}</p>
        </div>
      </div>

      <span className={`robot-status-pill ${statusType}`}>
        {statusLabel}
      </span>
    </div>
  )
}

export default RobotRow

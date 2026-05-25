import { getRobotStatusLabel, getRobotStatusType } from '../utils/robotUtils'

function RobotRow({ robot, isSelected, onSelectRobot }) {
  const batteryDisplay =
    robot.battery === null || robot.battery === undefined
      ? '—'
      : `${robot.battery}%`

  const statusType = getRobotStatusType(robot.status)

  return (
    <div
      className={`robot-row ${isSelected ? 'selected' : ''}`}
      onClick={() => onSelectRobot(robot.id)}
    >
      <div className="robot-left">
        <span className={`robot-dot ${statusType}`}></span>

        <div className="robot-info">
          <h3>{robot.name || `Robot ${robot.id}`}</h3>
          <p>{getRobotStatusLabel(robot.status)}</p>
        </div>
      </div>

      <span className="robot-battery">{batteryDisplay}</span>
    </div>
  )
}

export default RobotRow
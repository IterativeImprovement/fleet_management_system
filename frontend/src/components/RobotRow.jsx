function RobotRow({ robot, isSelected, onSelectRobot }) {
  const batteryDisplay =
    robot.battery === null || robot.battery === undefined
      ? '—'
      : `${robot.battery}%`

  return (
    <div
      className={`robot-row ${isSelected ? 'selected' : ''}`}
      onClick={() => onSelectRobot(robot.id)}
    >
      <div className="robot-left">
        <span className={`robot-dot ${robot.statusType}`}></span>

        <div className="robot-info">
          <h3>{robot.id}</h3>
          <p>{robot.status || 'Unknown'}</p>
        </div>
      </div>

      <span className="robot-battery">{batteryDisplay}</span>
    </div>
  )
}

export default RobotRow
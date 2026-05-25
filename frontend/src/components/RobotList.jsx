import RobotRow from "./RobotRow"

function RobotList({ robots, selectedRobotId, onSelectRobot }) {
    return (
        <div className="robot-panel">
            {robots.map(robot => (
                <RobotRow 
                    robot={robot} 
                    key={robot.id} 
                    isSelected={String(robot.id) === String(selectedRobotId)}
                    onSelectRobot={onSelectRobot}
                    />))}
        </div>
    )
}

export default RobotList

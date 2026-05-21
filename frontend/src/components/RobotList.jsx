import RobotRow from "./RobotRow"

function RobotList({ robots, selectedRobotId, onSelectRobot }) {
    return (
        <div className="robot-panel">
            {robots.map(robot => (
                <RobotRow 
                    robot={robot} 
                    key={robot.id} 
                    isSelected={robot.id === selectedRobotId}
                    onSelectRobot={onSelectRobot}
                    />))}
        </div>
    )
}

export default RobotList
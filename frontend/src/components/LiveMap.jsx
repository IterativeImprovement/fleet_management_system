function LiveMap({ robots = [], obstacles = [], selectedRobotId, onSelectRobot }) {
  function getPolylinePoints(path) {
    return path.map(point => `${point.x},${point.y}`).join(' ')
  }

  function hasMapPosition(robot) {
    return Number.isFinite(robot.x) && Number.isFinite(robot.y)
  }

  return (
    <section className="map">
      <div className="map-header">
        <div>
          <h2>Live Map</h2>
          <p>Real-time robot route monitoring</p>
        </div>
      </div>

      <div className="map-canvas">
        <svg className="route-layer" viewBox="0 0 100 100" preserveAspectRatio="none">
          {robots.map(robot => (
            robot.path?.length > 1 && (
              <polyline
                key={robot.id}
                points={getPolylinePoints(robot.path)}
                className={`route-line ${robot.statusType} ${
                  robot.id === selectedRobotId ? 'selected' : ''
                }`}
                onClick={() => onSelectRobot(robot.id)}
              />
            )
          ))}
        </svg>

        {robots
          .filter(hasMapPosition)
          .map(robot => (
            <div
              className={`map-robot-marker ${robot.statusType} ${
                robot.id === selectedRobotId ? 'selected' : ''
              }`}
              key={robot.id}
              style={{
                left: `${robot.x}%`,
                top: `${robot.y}%`,
              }}
              onClick={() => onSelectRobot(robot.id)}
            >
              <span className="marker-dot"></span>
              <span className="marker-label">{robot.id}</span>
            </div>
          ))}

        {obstacles.map(obstacle => (
          <div
            className="map-obstacle-marker"
            key={obstacle.id}
            style={{
              left: `${obstacle.x}%`,
              top: `${obstacle.y}%`,
            }}
          >
            <span className="obstacle-icon">!</span>
            <span className="obstacle-label">{obstacle.label}</span>
          </div>
        ))}
      </div>
    </section>
  )
}

export default LiveMap
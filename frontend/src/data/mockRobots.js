import { createRobot } from '../utils/robotUtils'

export const mockRobots = [
  createRobot({
    id: 1,
    name: 'R-001',
    type: 'STANDARD',
    status: 'ASSIGNED',
    speed: 10.0,
    position: { latitude: 1.3081, longitude: 103.8551 },
    taskIds: [1],
  }),

  createRobot({
    id: 2,
    name: 'R-002',
    type: 'STANDARD',
    status: 'CHARGING',
    speed: 0.0,
    position: { latitude: 1.2838, longitude: 103.8450 },
    taskIds: [],
  }),

  createRobot({
    id: 3,
    name: 'R-003',
    type: 'STANDARD',
    status: 'ERROR',
    speed: 0.0,
    position: { latitude: 1.2894, longitude: 103.8218 },
    taskIds: [3],
  }),
]

import { createRobot } from '../utils/robotUtils'

export const mockRobots = [
  createRobot({
    id: 1,
    name: 'R-001',
    type: 0,
    status: 1,
    speed: 10.0,
    battery: 87,
    x: 103.8551,
    y: 1.3081,
    latitude: 1.3081,
    longitude: 103.8551,
    route: 'Depot → Loading Bay',
    tasks: [1],
    path: [
      { latitude: 1.3075, longitude: 103.8528 },
      { latitude: 1.3079, longitude: 103.8542 },
      { latitude: 1.3081, longitude: 103.8551 },
      { latitude: 1.3084, longitude: 103.8560 },
    ],
  }),

  createRobot({
    id: 2,
    name: 'R-002',
    type: 0,
    status: 5,
    speed: 0.0,
    battery: 76,
    x: 103.8450,
    y: 1.2838,
    latitude: 1.2838,
    longitude: 103.8450,
    route: null,
    tasks: [],
    path: [
      { latitude: 1.2842, longitude: 103.8440 },
    ],
  }),

  createRobot({
    id: 3,
    name: 'R-003',
    type: 0,
    status: 9,
    speed: 0.0,
    battery: 67,
    x: 103.8218,
    y: 1.2894,
    latitude: 1.2894,
    longitude: 103.8218,
    route: 'Depot → Warehouse',
    tasks: [3],
    path: [
      { latitude: 1.2810, longitude: 103.8545 },
      { latitude: 1.2820, longitude: 103.8570 },
      { latitude: 1.2820, longitude: 103.8590 },
    ],
  }),
]

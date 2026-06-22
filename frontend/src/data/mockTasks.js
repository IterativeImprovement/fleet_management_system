import { createTask } from '../utils/taskUtils'

export const mockTasks = [
  createTask({
    id: 1,
    priority: 1,
    name: 'Tuas shipment',
    description: 'Transport from Tuas to Harbourfront.',
    type: 'StandardTransport',
    startDateTime: '2026-05-21T18:00:00',
    completionDateTime: '2026-05-21T19:30:00',
    startWayPoint: {
      id: 1,
      latitude: 1.3081,
      longitude: 103.8551,
    },
    endWayPoint: {
      id: 2,
      latitude: 1.2739,
      longitude: 103.8012,
    },
    robotId: 1,
    dependencyIds: [],
  }),

  createTask({
    id: 2,
    priority: 2,
    name: 'Warehouse delivery',
    description: 'Transport from warehouse to packing station.',
    type: 'StandardTransport',
    startDateTime: '2026-05-21T20:00:00',
    completionDateTime: '2026-05-21T21:00:00',
    startWayPoint: {
      id: 3,
      latitude: 1.3005,
      longitude: 103.8331,
    },
    endWayPoint: {
      id: 4,
      latitude: 1.2952,
      longitude: 103.8501,
    },
    robotId: null,
    dependencyIds: [],
  }),

  createTask({
    id: 3,
    priority: 3,
    name: 'Charging bay relocation',
    description: 'Move item from charging bay to storage zone.',
    type: 'StandardTransport',
    startDateTime: '2026-05-21T22:00:00',
    completionDateTime: '2026-05-21T22:45:00',
    startWayPoint: {
      id: 5,
      latitude: 1.2894,
      longitude: 103.8218,
    },
    endWayPoint: {
      id: 6,
      latitude: 1.2857,
      longitude: 103.8342,
    },
    robotId: 3,
    dependencyIds: [],
  }),
]

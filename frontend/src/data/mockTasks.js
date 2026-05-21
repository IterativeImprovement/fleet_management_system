import { createTask } from '../utils/taskUtils'

export const mockTasks = [
  createTask({
    id: 'T-101',
    priority: 'High',
    start: 'Depot A',
    destination: 'Loading Bay',
    status: 'Assigned',
    assignedRobotId: 'R-001',
    eta: '00:08:20',
  }),

  createTask({
    id: 'T-102',
    priority: 'Medium',
    start: 'Warehouse',
    destination: 'Packing Station',
  }),

  createTask({
    id: 'T-103',
    priority: 'Low',
    start: 'Charging Bay',
    destination: 'Storage Zone',
    status: 'Completed',
  }),
]
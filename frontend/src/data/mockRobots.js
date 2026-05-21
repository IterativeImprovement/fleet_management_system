export const mockRobots = [
  {
    id: 'R-001',
    status: 'Moving',
    statusType: 'moving',
    battery: 87,
    x: 30,
    y: 40,
    currentTask: 'T-101',
    eta: '00:08:20',
    location: 'Depot A',
    route: 'Depot → Loading Bay',

    path: [
      { x: 15, y: 70 },
      { x: 25, y: 55 },
      { x: 30, y: 40 },
      { x: 45, y: 30 },
    ],
  },
  {
    id: 'R-002',
    status: 'Idle',
    statusType: 'idle',
    battery: 76,
    x: 60,
    y: 55,
    currentTask: null,
    eta: '-',
    location: 'Warehouse',
    route: 'None',

    path: [
      { x: 60, y: 55 },
    ],
  },
  {
    id: 'R-003',
    status: 'Blocked',
    statusType: 'blocked',
    battery: 67,
    x: 45,
    y: 70,
    currentTask: 'T-203',
    eta: '00:12:32',
    location: 'Geylang Road',
    route: 'Depot → Warehouse',

    path: [
      { x: 20, y: 80 },
      { x: 35, y: 75 },
      { x: 45, y: 70 },
      { x: 55, y: 65 },
    ],
  },
]
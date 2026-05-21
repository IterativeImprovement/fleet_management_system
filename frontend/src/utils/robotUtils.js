export function createRobot({ id }) {
  return {
    id: id.trim(),

    status: 'Idle',
    statusType: 'idle',

    battery: null,
    x: null,
    y: null,
    currentTask: null,
    eta: '-',
    route: '-',
    path: [],

    location: 'Warehouse',
  }
}
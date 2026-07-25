// Encode/decode a simulation config to a shareable string, plus the unit
// conversions the config form uses. The string is reversible base64(JSON) (not
// a hash) so pasting it back reconstructs the exact same config + seed.

const CONFIG_VERSION = 1

export const SECONDS_PER_DAY = 86400
export const SECONDS_PER_HOUR = 3600

// Mirrors backend SimulationConfig defaults (canonical units: seconds /
// per-second). Only used for the first render — the encoded string always
// carries every field explicitly, so drift here can't corrupt a shared run.
// ponytail: keep in sync with SimulationConfig.java if fields change.
export const DEFAULT_CONFIG = {
  durationSeconds: 259200,
  numRobots: 10,
  startAtBase: true,
  baseLatitude: 1.351858,
  baseLongitude: 103.848890,
  taskArrivalRatePerSecond: 0.000193,
  malfunctionRatePerRobotPerSecond: 0.0000193,
  routeObstructionRatePerSecond: 0.0000386,
  minTaskCompletionSeconds: 10800,
  maxTaskCompletionSeconds: 36000,
  dependentTaskProbability: 0.3,
  maxDependentTasks: 1,
  dependencyPoolSize: 10,
  largestPriority: 5,
  smallestPriority: 1,
}

export function encodeConfig(config) {
  return btoa(JSON.stringify({ v: CONFIG_VERSION, ...config }))
}

export function decodeConfig(str) {
  let obj
  try {
    obj = JSON.parse(atob(String(str).trim()))
  } catch {
    throw new Error('Invalid configuration string')
  }
  if (obj == null || obj.v !== CONFIG_VERSION) {
    throw new Error('Invalid configuration string')
  }
  const config = { ...obj }
  delete config.v
  return config
}

export function resolveSimulationBasePosition(config) {
  const resolvedConfig = config == null
    ? DEFAULT_CONFIG
    : { ...DEFAULT_CONFIG, ...config }
  const latitude = resolvedConfig.baseLatitude
  const longitude = resolvedConfig.baseLongitude

  if (
    !Number.isFinite(latitude) ||
    !Number.isFinite(longitude) ||
    latitude < -90 ||
    latitude > 90 ||
    longitude < -180 ||
    longitude > 180
  ) {
    return null
  }

  return { latitude, longitude }
}

// Under 2^53 so it round-trips through JSON as a number without precision loss
// (Java accepts it as a long). Used when the user leaves the seed box blank.
export function randomSeed() {
  return Math.floor(Math.random() * 2 ** 53)
}

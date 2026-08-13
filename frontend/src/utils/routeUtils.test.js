import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  decodePolyline,
  hasValidTaskRouteEndpoints,
  interpolateAlongRoute,
  polylineLengthMeters,
  paceLegDuration,
} from './routeUtils.js'

// Standard Google polyline example.
const GOOGLE_ENCODED = '_p~iF~ps|U_ulLnnqC_mqNvxq`@'
const GOOGLE_POINTS = [
  [38.5, -120.2],
  [40.7, -120.95],
  [43.252, -126.453],
]

test('decodePolyline: decodes the known Google vector', () => {
  const points = decodePolyline(GOOGLE_ENCODED)

  assert.equal(points.length, GOOGLE_POINTS.length)
  points.forEach(([lat, lng], i) => {
    assert.ok(Math.abs(lat - GOOGLE_POINTS[i][0]) < 1e-5)
    assert.ok(Math.abs(lng - GOOGLE_POINTS[i][1]) < 1e-5)
  })
})

test('decodePolyline: empty/falsy input yields []', () => {
  assert.deepEqual(decodePolyline(''), [])
  assert.deepEqual(decodePolyline(null), [])
})

test('interpolateAlongRoute: clamps to endpoints and hits the midpoint', () => {
  const coords = [[0, 0], [0, 2]] // 2° of longitude
  assert.deepEqual(interpolateAlongRoute(coords, 0), { latitude: 0, longitude: 0 })
  assert.deepEqual(interpolateAlongRoute(coords, 1), { latitude: 0, longitude: 2 })
  assert.deepEqual(interpolateAlongRoute(coords, 0.5), { latitude: 0, longitude: 1 })
  assert.equal(interpolateAlongRoute([], 0.5), null)
})

test('interpolateAlongRoute: multi-segment progress apportions by length', () => {
  // First segment length 1, second length 3 gives total 4. progress 0.5 gives distance 2,
  // which lands 1 unit into the second segment (at lng 2).
  const coords = [[0, 0], [0, 1], [0, 4]]
  const mid = interpolateAlongRoute(coords, 0.5)
  assert.ok(Math.abs(mid.longitude - 2) < 1e-9)
})

test('polylineLengthMeters: sums segment lengths in metres', () => {
  // 1° here is ~111320 m; a two-segment 0 to 1 to 3 line spans 3° total.
  assert.equal(polylineLengthMeters([[0, 0], [0, 3]]), 3 * 111320)
  assert.equal(polylineLengthMeters([[0, 0]]), 0)
})

test('paceLegDuration: distance/speed, floored, with speed fallback', () => {
  const coords = [[0, 0], [0, 1]] // ~111320 m
  // 111320 m at 100 m/s = 1113.2 s, above the floor
  assert.ok(Math.abs(paceLegDuration(coords, 100) - 111320 / 100) < 1e-6)
  // tiny leg floors to the minimum
  assert.equal(paceLegDuration([[0, 0], [0, 1e-6]], 100, 60), 60)
  // zero/negative speed falls back to the nominal (never divides by zero / returns NaN)
  assert.ok(Number.isFinite(paceLegDuration(coords, 0)))
})

test('concatenated legs stay continuous (no teleport at the junction)', () => {
  // A connecting leg ending where the task route begins must feed one continuous path.
  const connecting = [[1.0, 103.0], [1.1, 103.1]]
  const taskCoords = [[1.1, 103.1], [1.2, 103.2], [1.3, 103.3]]
  const coords = connecting.concat(taskCoords)

  // Endpoints match the true start (connecting[0]) and true end (taskCoords[last]).
  assert.deepEqual(interpolateAlongRoute(coords, 0), { latitude: 1.0, longitude: 103.0 })
  assert.deepEqual(interpolateAlongRoute(coords, 1), { latitude: 1.3, longitude: 103.3 })

  // Sampling forward never jumps backwards (monotonic, continuous).
  let prev = -Infinity
  for (let p = 0; p <= 1.0001; p += 0.1) {
    const pos = interpolateAlongRoute(coords, Math.min(p, 1))
    assert.ok(pos.latitude >= prev - 1e-9)
    prev = pos.latitude
  }
})

test('hasValidTaskRouteEndpoints: true with finite start+end coords', () => {
  const task = {
    startLocation: { latitude: 1.3, longitude: 103.8 },
    endLocation: { latitude: 1.4, longitude: 103.9 },
  }
  assert.equal(hasValidTaskRouteEndpoints(task), true)
})

test('hasValidTaskRouteEndpoints: false when an endpoint is missing/non-numeric', () => {
  assert.equal(hasValidTaskRouteEndpoints(null), false)
  assert.equal(
    hasValidTaskRouteEndpoints({ startLocation: { latitude: 1.3, longitude: 103.8 } }),
    false
  )
  assert.equal(
    hasValidTaskRouteEndpoints({
      startLocation: { latitude: 'x', longitude: 103.8 },
      endLocation: { latitude: 1.4, longitude: 103.9 },
    }),
    false
  )
})

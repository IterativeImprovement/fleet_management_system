import { test } from 'node:test'
import assert from 'node:assert/strict'
import { decodePolyline, hasValidTaskRouteEndpoints } from './routeUtils.js'

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

import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  DEFAULT_CONFIG,
  encodeConfig,
  decodeConfig,
  resolveSimulationBasePosition,
} from './simConfigCodec.js'

test('encode/decode round-trips a config (incl. seed)', () => {
  const cfg = { ...DEFAULT_CONFIG, numRobots: 5, seed: 123456789 }
  assert.deepEqual(decodeConfig(encodeConfig(cfg)), cfg)
})

test('a garbage string is rejected', () => {
  assert.throws(() => decodeConfig('not-a-real-string'), /Invalid configuration string/)
})

test('an old/unsupported version is rejected', () => {
  const stale = btoa(JSON.stringify({ v: 0, numRobots: 5 }))
  assert.throws(() => decodeConfig(stale), /Invalid configuration string/)
})

test('base position uses the default coordinates when config is missing', () => {
  assert.deepEqual(resolveSimulationBasePosition(), {
    latitude: DEFAULT_CONFIG.baseLatitude,
    longitude: DEFAULT_CONFIG.baseLongitude,
  })
  assert.deepEqual(resolveSimulationBasePosition({}), {
    latitude: DEFAULT_CONFIG.baseLatitude,
    longitude: DEFAULT_CONFIG.baseLongitude,
  })
})

test('base position accepts finite custom coordinates in geographical range', () => {
  assert.deepEqual(
    resolveSimulationBasePosition({
      baseLatitude: -33.8688,
      baseLongitude: 151.2093,
    }),
    { latitude: -33.8688, longitude: 151.2093 },
  )
  assert.deepEqual(
    resolveSimulationBasePosition({
      baseLatitude: 90,
      baseLongitude: -180,
    }),
    { latitude: 90, longitude: -180 },
  )
})

test('base position rejects invalid explicit coordinates', () => {
  const invalidConfigs = [
    { baseLatitude: undefined },
    { baseLongitude: '103.848890' },
    { baseLatitude: Number.NaN },
    { baseLongitude: Number.POSITIVE_INFINITY },
    { baseLatitude: -90.000001 },
    { baseLatitude: 90.000001 },
    { baseLongitude: -180.000001 },
    { baseLongitude: 180.000001 },
  ]

  invalidConfigs.forEach(config => {
    assert.equal(resolveSimulationBasePosition(config), null)
  })
})

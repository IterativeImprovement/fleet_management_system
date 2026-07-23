import { test } from 'node:test'
import assert from 'node:assert/strict'
import { DEFAULT_CONFIG, encodeConfig, decodeConfig } from './simConfigCodec.js'

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

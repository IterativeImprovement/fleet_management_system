import { useEffect, useState } from 'react'
import {
  DEFAULT_CONFIG,
  SECONDS_PER_DAY,
  SECONDS_PER_HOUR,
  encodeConfig,
  decodeConfig,
  randomSeed,
} from '../utils/simConfigCodec.js'

function toFriendly(cfg) {
  return {
    numRobots: String(cfg.numRobots),
    durationDays: String(cfg.durationSeconds / SECONDS_PER_DAY),
    tasksPerDay: String(cfg.taskArrivalRatePerSecond * SECONDS_PER_DAY),
    malfPerDay: String(cfg.malfunctionRatePerRobotPerSecond * SECONDS_PER_DAY),
    obstrPerDay: String(cfg.routeObstructionRatePerSecond * SECONDS_PER_DAY),
    priorityMin: String(cfg.smallestPriority),
    priorityMax: String(cfg.largestPriority),
    completionMinHours: String(cfg.minTaskCompletionSeconds / SECONDS_PER_HOUR),
    completionMaxHours: String(cfg.maxTaskCompletionSeconds / SECONDS_PER_HOUR),
  }
}

// Preserve unchanged canonical values to avoid conversion precision loss.
function buildConfig(f, seed, base) {
  const init = toFriendly(base)
  const resolve = (cur, key, toCanon) =>
    cur === init[key] ? base[canon(key)] : toCanon(Number(cur))

  const cfg = {
    ...base,
    numRobots: resolve(f.numRobots, 'numRobots', v => Math.round(v)),
    durationSeconds: resolve(f.durationDays, 'durationDays', v => v * SECONDS_PER_DAY),
    taskArrivalRatePerSecond: resolve(f.tasksPerDay, 'tasksPerDay', v => v / SECONDS_PER_DAY),
    malfunctionRatePerRobotPerSecond: resolve(f.malfPerDay, 'malfPerDay', v => v / SECONDS_PER_DAY),
    routeObstructionRatePerSecond: resolve(f.obstrPerDay, 'obstrPerDay', v => v / SECONDS_PER_DAY),
    smallestPriority: resolve(f.priorityMin, 'priorityMin', v => Math.round(v)),
    largestPriority: resolve(f.priorityMax, 'priorityMax', v => Math.round(v)),
    minTaskCompletionSeconds: resolve(f.completionMinHours, 'completionMinHours', v => v * SECONDS_PER_HOUR),
    maxTaskCompletionSeconds: resolve(f.completionMaxHours, 'completionMaxHours', v => v * SECONDS_PER_HOUR),
  }

  if (seed.trim() !== '') cfg.seed = Number(seed)
  else delete cfg.seed
  return cfg
}

// map a friendly-field key to its canonical config key (for untouched-field passthrough)
function canon(key) {
  return {
    numRobots: 'numRobots',
    durationDays: 'durationSeconds',
    tasksPerDay: 'taskArrivalRatePerSecond',
    malfPerDay: 'malfunctionRatePerRobotPerSecond',
    obstrPerDay: 'routeObstructionRatePerSecond',
    priorityMin: 'smallestPriority',
    priorityMax: 'largestPriority',
    completionMinHours: 'minTaskCompletionSeconds',
    completionMaxHours: 'maxTaskCompletionSeconds',
  }[key]
}

function validateConfig(cfg, seed) {
  if (!Number.isInteger(cfg.numRobots) || cfg.numRobots < 1) return 'Number of robots must be a whole number >= 1'
  if (!(cfg.durationSeconds > 0)) return 'Duration must be greater than 0'
  if (cfg.taskArrivalRatePerSecond < 0 || cfg.malfunctionRatePerRobotPerSecond < 0 || cfg.routeObstructionRatePerSecond < 0)
    return 'Rates cannot be negative'
  if (cfg.smallestPriority < 1 || cfg.largestPriority < cfg.smallestPriority)
    return 'Priority range is invalid (min >= 1 and <= max)'
  if (!(cfg.minTaskCompletionSeconds > 0) || cfg.maxTaskCompletionSeconds < cfg.minTaskCompletionSeconds)
    return 'Task completion range is invalid (min <= max)'
  if (seed.trim() !== '' && !Number.isFinite(Number(seed))) return 'Seed must be a number'
  return ''
}

function SimulationConfigForm({ onConfigChange, disabled }) {
  const [baseConfig, setBaseConfig] = useState(DEFAULT_CONFIG)
  const [f, setF] = useState(() => toFriendly(DEFAULT_CONFIG))
  const [seed, setSeed] = useState(() => String(randomSeed()))
  const [configString, setConfigString] = useState('')
  const [loadError, setLoadError] = useState('')

  // Derived each render (pure) - no state needed for validation.
  const config = buildConfig(f, seed, baseConfig)
  const validationError = validateConfig(config, seed)
  const shownError = loadError || validationError

  // Lift the resolved config + validation error up to Topbar whenever the user
  // changes something. Deps are our own state (stable across parent re-renders,
  // so no loop) and onConfigChange must be a stable callback.
  useEffect(() => {
    const cfg = buildConfig(f, seed, baseConfig)
    onConfigChange(cfg, validateConfig(cfg, seed))
  }, [f, seed, baseConfig, onConfigChange])

  const setField = (key, value) => setF(prev => ({ ...prev, [key]: value }))

  function handleCopy() {
    let s = seed.trim()
    if (s === '') {
      s = String(randomSeed())
      setSeed(s)
    }
    const encoded = encodeConfig({ ...buildConfig(f, s, baseConfig), seed: Number(s) })
    setConfigString(encoded)
    navigator.clipboard?.writeText(encoded).catch(() => { })
  }

  function handleLoad() {
    try {
      const decoded = { ...DEFAULT_CONFIG, ...decodeConfig(configString) }
      setBaseConfig(decoded)
      setF(toFriendly(decoded))
      setSeed(decoded.seed != null ? String(decoded.seed) : '')
      setLoadError('')
    } catch (e) {
      setLoadError(e.message)
    }
  }

  return (
    <div className="sim-config-form">
      <h3>Simulation Configuration</h3>

      <div className="sim-config-grid">
        <label>
          Number of robots
          <input type="number" min="1" step="1" value={f.numRobots} disabled={disabled}
            onChange={e => setField('numRobots', e.target.value)} />
        </label>
        <label>
          Duration (days)
          <input type="number" min="0" step="0.1" value={f.durationDays} disabled={disabled}
            onChange={e => setField('durationDays', e.target.value)} />
        </label>
        <label>
          New tasks / day
          <input type="number" min="0" step="any" value={f.tasksPerDay} disabled={disabled}
            onChange={e => setField('tasksPerDay', e.target.value)} />
        </label>
        <label>
          Malfunctions / robot / day
          <input type="number" min="0" step="any" value={f.malfPerDay} disabled={disabled}
            onChange={e => setField('malfPerDay', e.target.value)} />
        </label>
        <label>
          Road obstructions / day
          <input type="number" min="0" step="any" value={f.obstrPerDay} disabled={disabled}
            onChange={e => setField('obstrPerDay', e.target.value)} />
        </label>
        <label>
          Priority min
          <input type="number" min="1" step="1" value={f.priorityMin} disabled={disabled}
            onChange={e => setField('priorityMin', e.target.value)} />
        </label>
        <label>
          Priority max
          <input type="number" min="1" step="1" value={f.priorityMax} disabled={disabled}
            onChange={e => setField('priorityMax', e.target.value)} />
        </label>
        <label>
          Task time min (hours)
          <input type="number" min="0" step="0.5" value={f.completionMinHours} disabled={disabled}
            onChange={e => setField('completionMinHours', e.target.value)} />
        </label>
        <label>
          Task time max (hours)
          <input type="number" min="0" step="0.5" value={f.completionMaxHours} disabled={disabled}
            onChange={e => setField('completionMaxHours', e.target.value)} />
        </label>
      </div>

      <label className="sim-config-seed">
        Seed
        <div className="sim-config-seed-row">
          <input type="text" value={seed} disabled={disabled} placeholder="Random"
            onChange={e => setSeed(e.target.value)} />
          <button type="button" disabled={disabled} onClick={() => setSeed(String(randomSeed()))}>🎲</button>
        </div>
      </label>

      <label className="sim-config-string">
        Configuration string (share to reproduce this exact run)
        <textarea rows="2" value={configString} disabled={disabled} placeholder="Paste a string here and click Load..."
          onChange={e => { setConfigString(e.target.value); setLoadError('') }} />
      </label>

      {shownError && <p className="form-error">{shownError}</p>}

      <div className="form-actions">
        <button type="button" disabled={disabled} onClick={handleCopy}>Copy config</button>
        <button type="button" disabled={disabled} onClick={handleLoad}>Load</button>
      </div>
    </div>
  )
}

export default SimulationConfigForm

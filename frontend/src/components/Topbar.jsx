import { useState } from 'react'

const SPEED_OPTIONS = [
  { label: '0.5×', factor: 1080 },
  { label: '1×', factor: 2160 },
  { label: '2×', factor: 4320 },
  { label: '4×', factor: 8640 },
]

function Topbar({
  simTimeDisplay,
  isRunning,
  simulationId,
  speedFactor,
  onStart,
  onPause,
  onResume,
  onReset,
  onSpeedChange,
}) {
  const [seed, setSeed] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)

  const hasSimulation = simulationId != null

  async function handleStart() {
    setError(null)
    setIsLoading(true)
    try {
      await onStart(seed.trim() === '' ? null : seed.trim())
    } catch (err) {
      setError(err.message)
    } finally {
      setIsLoading(false)
    }
  }

  async function handleReset() {
    setError(null)
    setIsLoading(true)
    try {
      await onReset()
    } catch (err) {
      setError(err.message)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <header className='topbar'>
      <h1>NoClash</h1>

      <div className='sim-clock'>
        <span className='sim-clock-label'>Simulation Time</span>
        <span className='sim-time'>{simTimeDisplay}</span>
      </div>

      <div className='sim-controls'>
        <label className='sim-speed-label'>
          Speed
          <select
            className='sim-speed-select'
            value={speedFactor}
            onChange={e => onSpeedChange(Number(e.target.value))}
          >
            {SPEED_OPTIONS.map(opt => (
              <option key={opt.factor} value={opt.factor}>{opt.label}</option>
            ))}
          </select>
        </label>

        {!hasSimulation ? (
          <>
            <input
              className='sim-seed-input'
              type='text'
              placeholder='Seed (optional)'
              value={seed}
              onChange={e => setSeed(e.target.value)}
              disabled={isLoading}
            />
            <button
              className='sim-btn sim-btn-start'
              onClick={handleStart}
              disabled={isLoading}
            >
              {isLoading ? 'Generating…' : '▶ Start Simulation'}
            </button>
          </>
        ) : (
          <>
            {isRunning ? (
              <button className='sim-btn sim-btn-pause' onClick={onPause}>
                ⏸ Pause
              </button>
            ) : (
              <button className='sim-btn sim-btn-resume' onClick={onResume}>
                ▶ Resume
              </button>
            )}

            <button
              className='sim-btn sim-btn-reset'
              onClick={handleReset}
              disabled={isLoading}
            >
              {isLoading ? 'Resetting…' : '↺ Reset'}
            </button>
          </>
        )}

        {error && <span className='sim-error'>{error}</span>}
      </div>
    </header>
  )
}

export default Topbar

import { useCallback, useState } from 'react'
import SimulationConfigForm from './SimulationConfigForm.jsx'

const SPEED_OPTIONS = [
  { label: '0.5×', factor: 180 },
  { label: '1×', factor: 360 },
  { label: '2×', factor: 720 },
  { label: '4×', factor: 1440 },
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
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)
  const [showConfig, setShowConfig] = useState(false)
  const [config, setConfig] = useState(null)
  const [configError, setConfigError] = useState('')

  const hasSimulation = simulationId != null

  // stable so SimulationConfigForm's lift-up effect doesn't refire every render
  const handleConfigChange = useCallback((cfg, err) => {
    setConfig(cfg)
    setConfigError(err)
  }, [])

  async function handleStart() {
    setError(null)
    setIsLoading(true)
    try {
      await onStart(config)
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
            <button
              className='sim-btn sim-btn-config'
              onClick={() => setShowConfig(v => !v)}
              disabled={isLoading}
            >
              ⚙ Configure
            </button>
            <button
              className='sim-btn sim-btn-start'
              onClick={handleStart}
              disabled={isLoading || !!configError}
            >
              {isLoading ? 'Generating…' : '▶ Start Simulation'}
            </button>
            {/* kept mounted (toggled via CSS) so edits persist when the panel closes */}
            <div className={`sim-config-panel${showConfig ? '' : ' sim-config-panel--hidden'}`}>
              <SimulationConfigForm onConfigChange={handleConfigChange} disabled={isLoading} />
            </div>
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

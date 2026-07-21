import { useState } from 'react'

function AlertLog({ alerts }) {
  const [open, setOpen] = useState(false)

  return (
    <section className='alert-log'>
      <button
        className="alert-log-header"
        onClick={() => setOpen(prev => !prev)}
        aria-expanded={open}
      >
        {open ? '▼' : '▲'} Alerts ({alerts.length})
      </button>

      {open && (
        <div className="alert-list">
          {alerts.map(alert => (
              <div className="alert-row" key={alert.id}>
                  [{alert.time}] {alert.message}
              </div>
          ))}
        </div>
      )}
    </section>
  )
}

export default AlertLog

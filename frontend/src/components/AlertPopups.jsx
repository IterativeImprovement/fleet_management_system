import { useState, useRef, useEffect } from 'react'

const POPUP_TYPES = ['Malfunction', 'Obstruction']
const POPUP_DURATION_MS = 6000

function AlertPopups({ alerts }) {
  const [popups, setPopups] = useState([])
  const seenIdsRef = useRef(new Set())

  useEffect(() => {
    if (alerts.length === 0) {
      // Sim reset restarts the alert id counter, so old ids must not block new popups.
      // Any popups still on screen expire via their own timers.
      seenIdsRef.current.clear()
      return
    }

    alerts.forEach(alert => {
      if (!POPUP_TYPES.includes(alert.type)) return
      if (seenIdsRef.current.has(alert.id)) return

      seenIdsRef.current.add(alert.id)
      setPopups(prev => [...prev, alert])

      setTimeout(() => {
        setPopups(prev => prev.filter(popup => popup.id !== alert.id))
      }, POPUP_DURATION_MS)
    })
  }, [alerts])

  return (
    <div className="alert-popups">
      {popups.slice(-5).map(popup => (
        <div
          className={`alert-popup alert-popup-${popup.type.toLowerCase()}`}
          key={popup.id}
        >
          [{popup.time}] {popup.message}
        </div>
      ))}
    </div>
  )
}

export default AlertPopups

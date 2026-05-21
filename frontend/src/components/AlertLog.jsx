function AlertLog({ alerts }) {
  return (
    <section className='alert-log'>
      <h2>Alerts</h2>

      <div className="alert-list">
        {alerts.map(alert => (
            <div className="alert-row" key={alert.id}>
                [{alert.time}] {alert.message}
            </div>
        ))}
      </div>
    </section>
  )
}

export default AlertLog
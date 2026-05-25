export function decodePolyline(encoded) {
  if (!encoded) return []

  let index = 0
  let lat = 0
  let lng = 0
  const coordinates = []

  while (index < encoded.length) {
    let result = 0
    let shift = 0
    let byte

    do {
      byte = encoded.charCodeAt(index++) - 63
      result |= (byte & 0x1f) << shift
      shift += 5
    } while (byte >= 0x20)

    lat += result & 1 ? ~(result >> 1) : result >> 1

    result = 0
    shift = 0

    do {
      byte = encoded.charCodeAt(index++) - 63
      result |= (byte & 0x1f) << shift
      shift += 5
    } while (byte >= 0x20)

    lng += result & 1 ? ~(result >> 1) : result >> 1

    coordinates.push([lat / 1e5, lng / 1e5])
  }

  return coordinates
}

export function hasValidTaskRouteEndpoints(task) {
  return (
    Number.isFinite(Number(task?.startWayPoint?.latitude)) &&
    Number.isFinite(Number(task?.startWayPoint?.longitude)) &&
    Number.isFinite(Number(task?.endWayPoint?.latitude)) &&
    Number.isFinite(Number(task?.endWayPoint?.longitude))
  )
}

export function getTaskRouteEndpoints(task) {
  const startLat = Number(task.startWayPoint.latitude)
  const startLng = Number(task.startWayPoint.longitude)
  const endLat = Number(task.endWayPoint.latitude)
  const endLng = Number(task.endWayPoint.longitude)

  return {
    start: `${startLat},${startLng}`,
    end: `${endLat},${endLng}`,
  }
}

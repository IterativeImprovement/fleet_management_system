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
    Number.isFinite(Number(task?.startLocation?.latitude)) &&
    Number.isFinite(Number(task?.startLocation?.longitude)) &&
    Number.isFinite(Number(task?.endLocation?.latitude)) &&
    Number.isFinite(Number(task?.endLocation?.longitude))
  )
}

export function getTaskRouteEndpoints(task) {
  const startLat = Number(task.startLocation.latitude)
  const startLng = Number(task.startLocation.longitude)
  const endLat = Number(task.endLocation.latitude)
  const endLng = Number(task.endLocation.longitude)

  return {
    start: `${startLat},${startLng}`,
    end: `${endLat},${endLng}`,
  }
}

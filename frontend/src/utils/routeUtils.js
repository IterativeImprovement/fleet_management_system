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

// Walk a list of [lat, lng] waypoints and return the position at `progress` (0–1),
// apportioning progress across segments by their length. Continuous within a route.
export function interpolateAlongRoute(coords, progress) {
  if (!coords || coords.length === 0) return null
  if (coords.length === 1) return { latitude: coords[0][0], longitude: coords[0][1] }
  if (progress <= 0) return { latitude: coords[0][0], longitude: coords[0][1] }
  if (progress >= 1) return { latitude: coords[coords.length - 1][0], longitude: coords[coords.length - 1][1] }

  const segLengths = []
  let totalLength = 0
  for (let i = 0; i < coords.length - 1; i++) {
    const dLat = coords[i + 1][0] - coords[i][0]
    const dLng = coords[i + 1][1] - coords[i][1]
    const len = Math.sqrt(dLat * dLat + dLng * dLng)
    segLengths.push(len)
    totalLength += len
  }

  const target = progress * totalLength
  let accumulated = 0
  for (let i = 0; i < segLengths.length; i++) {
    if (accumulated + segLengths[i] >= target) {
      const t = segLengths[i] === 0 ? 0 : (target - accumulated) / segLengths[i]
      return {
        latitude: coords[i][0] + t * (coords[i + 1][0] - coords[i][0]),
        longitude: coords[i][1] + t * (coords[i + 1][1] - coords[i][1]),
      }
    }
    accumulated += segLengths[i]
  }

  const last = coords[coords.length - 1]
  return { latitude: last[0], longitude: last[1] }
}

const METERS_PER_DEGREE = 111320 // rough; fine for Singapore (~1.3°N)

// Total length of a [lat, lng] polyline, in metres.
export function polylineLengthMeters(coords) {
  if (!coords || coords.length < 2) return 0
  let m = 0
  for (let i = 0; i < coords.length - 1; i++) {
    const dLat = coords[i + 1][0] - coords[i][0]
    const dLng = coords[i + 1][1] - coords[i][1]
    m += Math.sqrt(dLat * dLat + dLng * dLng) * METERS_PER_DEGREE
  }
  return m
}

// Sim-seconds for a robot of speed `speedMps` to travel `coords`, floored at
// `minSeconds` so every leg has a positive, non-zero duration.
// Uses an approximate degree-to-metre conversion and a minimum travel time.
export function paceLegDuration(coords, speedMps, minSeconds = 60) {
  const mps = speedMps > 0 ? speedMps : 1.5 // fallback nominal speed
  return Math.max(polylineLengthMeters(coords) / mps, minSeconds)
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

import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { getRobotStatusType } from '../utils/robotUtils'

const SINGAPORE_CENTER = [1.3521, 103.8198]
const SINGAPORE_BOUNDS = L.latLngBounds(
  [1.16, 103.502],
  [1.56073, 104.11475]
)
const ONEMAP_MIN_ZOOM = 11
const ONEMAP_MAX_ZOOM = 19
const SINGAPORE_START_ZOOM = 13

const BASE_ICON = L.divIcon({
  className: 'map-base-div-icon',
  html: `
    <div class="map-base-marker" aria-hidden="true">
      <span class="map-base-marker-badge">
        <svg viewBox="0 0 24 24" focusable="false" aria-hidden="true">
          <path d="M4 10.5 12 4l8 6.5v8.25A1.25 1.25 0 0 1 18.75 20H5.25A1.25 1.25 0 0 1 4 18.75V10.5Z" />
          <path d="M9 20v-6h6v6" />
        </svg>
      </span>
    </div>
  `,
  iconSize: [34, 42],
  iconAnchor: [17, 42],
  tooltipAnchor: [0, -36],
})

// Must match the repair coordinates in backend KeyLocations.
const REPAIR_LOCATION = [1.333425, 103.760141]

const REPAIR_ICON = L.divIcon({
  className: 'map-repair-div-icon',
  html: `
    <div class="map-repair-marker" aria-hidden="true">
      <span class="map-repair-marker-badge">
        <svg viewBox="0 0 24 24" focusable="false" aria-hidden="true">
          <path d="M14.7 6.3a4 4 0 0 0-5.4 4.9L4 16.5V20h3.5l5.3-5.3a4 4 0 0 0 4.9-5.4l-2.6 2.6-2-2Z" />
        </svg>
      </span>
    </div>
  `,
  iconSize: [34, 42],
  iconAnchor: [17, 42],
  tooltipAnchor: [0, -36],
})

const OBSTRUCTION_ICON = L.divIcon({
  className: 'map-obstacle-div-icon',
  html: `
    <div class="map-obstacle-marker-badge" aria-hidden="true">
      <svg viewBox="0 0 24 24" focusable="false" aria-hidden="true">
        <path d="M6 6 18 18M18 6 6 18" />
      </svg>
    </div>
  `,
  iconSize: [22, 22],
  iconAnchor: [11, 11],
  tooltipAnchor: [0, -12],
})

function getValidMapPosition(position) {
  if (
    !position ||
    position.latitude === null ||
    position.latitude === undefined ||
    position.latitude === '' ||
    position.longitude === null ||
    position.longitude === undefined ||
    position.longitude === ''
  ) {
    return null
  }

  const latitude = Number(position.latitude)
  const longitude = Number(position.longitude)

  if (
    !Number.isFinite(latitude) ||
    !Number.isFinite(longitude) ||
    latitude < -90 ||
    latitude > 90 ||
    longitude < -180 ||
    longitude > 180
  ) {
    return null
  }

  return [latitude, longitude]
}

function getSingaporeMinZoom(map) {
  const size = map.getSize()

  if (!size.x || !size.y) return SINGAPORE_START_ZOOM

  for (let zoom = ONEMAP_MIN_ZOOM; zoom <= ONEMAP_MAX_ZOOM; zoom += 1) {
    const projectedBounds = L.bounds(
      map.project(SINGAPORE_BOUNDS.getNorthWest(), zoom),
      map.project(SINGAPORE_BOUNDS.getSouthEast(), zoom)
    )
    const boundsSize = projectedBounds.getSize()

    if (boundsSize.x >= size.x && boundsSize.y >= size.y) {
      return zoom
    }
  }

  return ONEMAP_MAX_ZOOM
}

function keepMapInsideSingapore(map) {
  const minZoom = getSingaporeMinZoom(map)

  map.setMinZoom(minZoom)

  if (map.getZoom() < minZoom) {
    map.setZoom(minZoom, { animate: false })
  }

  map.panInsideBounds(SINGAPORE_BOUNDS, { animate: false })
}

function fitBoundsInsideSingapore(map, bounds, options) {
  keepMapInsideSingapore(map)
  map.fitBounds(bounds, { ...options, animate: false })
  keepMapInsideSingapore(map)
}

function LiveMap({
  robots = [],
  obstacles = [],
  activeBasePosition = null,
  routesByTaskId = {},
  currentRoutesByRobotId = {},
  selectedTaskId,
  selectedRobotId,
  isLoadingRoutes = false,
  routeErrorCount = 0,
  onSelectRobot,
  onSelectTask,
  coloredSegmentsByTaskId = {},
}) {
  const mapContainerRef = useRef(null)
  const mapRef = useRef(null)
  const routeLayerRef = useRef(null)
  const markerLayerRef = useRef(null)
  const baseLayerRef = useRef(null)
  const baseMarkerRef = useRef(null)
  const repairLayerRef = useRef(null)
  const obstacleLayerRef = useRef(null)
  const hasFittedAllRoutesRef = useRef(false)
  const markersByIdRef = useRef(new Map())
  const onSelectRobotRef = useRef(onSelectRobot)

  useEffect(() => {
    if (mapRef.current || !mapContainerRef.current) return

    mapRef.current = L.map(mapContainerRef.current, {
      center: SINGAPORE_CENTER,
      zoom: SINGAPORE_START_ZOOM,
      minZoom: SINGAPORE_START_ZOOM,
      maxZoom: ONEMAP_MAX_ZOOM,
      maxBounds: SINGAPORE_BOUNDS,
      maxBoundsViscosity: 1,
    })

    L.tileLayer('/map/tiles/{z}/{x}/{y}.png', {
      bounds: SINGAPORE_BOUNDS,
      detectRetina: true,
      maxZoom: ONEMAP_MAX_ZOOM,
      minZoom: ONEMAP_MIN_ZOOM,
      noWrap: true,
      attribution:
        '<img src="https://www.onemap.gov.sg/web-assets/images/logo/om_logo.png" style="height:20px;width:20px;" />&nbsp;<a href="https://www.onemap.gov.sg/" target="_blank" rel="noopener noreferrer">OneMap</a>&nbsp;&copy;&nbsp;contributors&nbsp;&#124;&nbsp;<a href="https://www.sla.gov.sg/" target="_blank" rel="noopener noreferrer">Singapore Land Authority</a>',
    }).addTo(mapRef.current)

    routeLayerRef.current = L.layerGroup().addTo(mapRef.current)
    markerLayerRef.current = L.layerGroup().addTo(mapRef.current)
    baseLayerRef.current = L.layerGroup().addTo(mapRef.current)
    repairLayerRef.current = L.layerGroup().addTo(mapRef.current)
    obstacleLayerRef.current = L.layerGroup().addTo(mapRef.current)

    const repairMarker = L.marker(REPAIR_LOCATION, {
      icon: REPAIR_ICON,
      keyboard: true,
      title: 'Repair Shop',
      alt: 'Repair Shop',
      riseOnHover: true,
      zIndexOffset: 500,
    })

    repairMarker.bindTooltip('Repair Shop', {
      permanent: false,
      direction: 'top',
      className: 'map-repair-tooltip',
      opacity: 1,
    })

    repairLayerRef.current.addLayer(repairMarker)

    const repairElement = repairMarker.getElement()
    if (repairElement) {
      repairElement.setAttribute('aria-label', 'Repair Shop')
      repairElement.setAttribute('role', 'img')
    }

    requestAnimationFrame(() => {
      if (!mapRef.current) return

      mapRef.current.invalidateSize()
      keepMapInsideSingapore(mapRef.current)
    })
  }, [])

  useEffect(() => {
    if (!mapContainerRef.current) return

    const resizeMap = () => {
      if (!mapRef.current) return

      mapRef.current.invalidateSize()
      keepMapInsideSingapore(mapRef.current)
    }

    const resizeObserver = new ResizeObserver(() => {
      requestAnimationFrame(resizeMap)
    })

    resizeObserver.observe(mapContainerRef.current)
    window.addEventListener('resize', resizeMap)

    return () => {
      resizeObserver.disconnect()
      window.removeEventListener('resize', resizeMap)
    }
  }, [])

  useEffect(() => {
    requestAnimationFrame(() => {
      if (!mapRef.current) return

      mapRef.current.invalidateSize()
      keepMapInsideSingapore(mapRef.current)
    })
  }, [selectedRobotId, selectedTaskId])

  useEffect(() => {
    if (!routeLayerRef.current) return

    routeLayerRef.current.clearLayers()

    const routeLines = []
    let selectedBounds = null

    // Show the selected task's planned route separately from the robot's active leg
    const selectedRoute = selectedTaskId != null ? routesByTaskId?.[selectedTaskId] : null
    if (selectedRoute && selectedRoute.coordinates && selectedRoute.coordinates.length >= 2) {
      const coloredSegments = coloredSegmentsByTaskId[selectedRoute.taskId]

      if (coloredSegments && coloredSegments.length > 0) {
        const segmentLines = []

        coloredSegments.forEach(segment => {
          if (!segment.coordinates || segment.coordinates.length < 2) return

          const line = L.polyline(segment.coordinates, {
            color: segment.color,
            weight: 6,
            opacity: 0.95,
            lineCap: 'round',
            lineJoin: 'round',
          })

          line.on('click', () => onSelectTask?.(selectedRoute.taskId))
          routeLayerRef.current.addLayer(line)
          segmentLines.push(line)
          routeLines.push(line)
        })

        if (segmentLines.length > 0) {
          selectedBounds = L.featureGroup(segmentLines).getBounds()
        }
      } else {
        const routeLine = L.polyline(selectedRoute.coordinates, {
          color: '#0a84ff',
          weight: 6,
          opacity: 0.95,
          lineCap: 'round',
          lineJoin: 'round',
        })

        routeLine.bindTooltip(`Task ${selectedRoute.taskId}`, { permanent: false, direction: 'top' })
        routeLine.on('click', () => onSelectTask?.(selectedRoute.taskId))
        routeLayerRef.current.addLayer(routeLine)
        routeLines.push(routeLine)
        selectedBounds = routeLine.getBounds()
      }
    }
    Object.values(currentRoutesByRobotId || {}).forEach(route => {
      if (!route.coordinates || route.coordinates.length < 2) return
      if (route.taskId != null && String(route.taskId) === String(selectedTaskId)) return // already highlighted above

      const routeLine = L.polyline(route.coordinates, {
        color: '#64748b',
        weight: 3,
        opacity: 0.45,
        lineCap: 'round',
        lineJoin: 'round',
      })

      if (route.taskId != null) {
        routeLine.bindTooltip(`Task ${route.taskId}`, { permanent: false, direction: 'top' })
        routeLine.on('click', () => onSelectTask?.(route.taskId))
      }

      routeLayerRef.current.addLayer(routeLine)
      routeLines.push(routeLine)
    })

    if (selectedBounds) {
      if (!mapRef.current) return
      fitBoundsInsideSingapore(mapRef.current, selectedBounds, { padding: [32, 32] })
      return
    }

    if (!hasFittedAllRoutesRef.current && routeLines.length > 0) {
      if (!mapRef.current) return
      const routeGroup = L.featureGroup(routeLines)
      fitBoundsInsideSingapore(mapRef.current, routeGroup.getBounds(), { padding: [32, 32] })
      hasFittedAllRoutesRef.current = true
    }
  }, [routesByTaskId, currentRoutesByRobotId, selectedTaskId, coloredSegmentsByTaskId, onSelectTask])

  useEffect(() => {
    if (!mapRef.current || !selectedRobotId) return

    const hasRouteForSelection =
      selectedTaskId != null &&
      (routesByTaskId?.[selectedTaskId]?.coordinates?.length ?? 0) >= 2

    if (hasRouteForSelection) return

    const robot = robots.find(r => String(r.id) === String(selectedRobotId))
    const position = getValidMapPosition(robot?.position)
    if (!position) return

    mapRef.current.panTo(position, { animate: true })
  }, [robots, selectedRobotId, selectedTaskId, routesByTaskId])

  useEffect(() => {
    const layer = baseLayerRef.current
    if (!layer) return

    const position = getValidMapPosition(activeBasePosition)

    if (!position) {
      if (baseMarkerRef.current) {
        layer.removeLayer(baseMarkerRef.current)
        baseMarkerRef.current = null
      }
      return
    }

    if (baseMarkerRef.current) {
      baseMarkerRef.current.setLatLng(position)
      return
    }

    const marker = L.marker(position, {
      icon: BASE_ICON,
      keyboard: true,
      title: 'Base',
      alt: 'Base',
      riseOnHover: true,
      zIndexOffset: 500,
    })

    marker.bindTooltip('Base', {
      permanent: false,
      direction: 'top',
      className: 'map-base-tooltip',
      opacity: 1,
    })

    layer.addLayer(marker)

    const element = marker.getElement()
    if (element) {
      element.setAttribute('aria-label', 'Base')
      element.setAttribute('role', 'img')
    }

    baseMarkerRef.current = marker
  }, [activeBasePosition])


  // Keep the click handler current without re-binding markers each render.
  useEffect(() => {
    onSelectRobotRef.current = onSelectRobot
  }, [onSelectRobot])

  // Update markers in place so clicks are not lost while robots move.
  useEffect(() => {
    if (!markerLayerRef.current) return

    const layer = markerLayerRef.current
    const markers = markersByIdRef.current
    const seen = new Set()

    robots
      .filter(robot =>
        robot.position !== null &&
        Number.isFinite(Number(robot.position.latitude)) &&
        Number.isFinite(Number(robot.position.longitude))
      )
      .forEach(robot => {
        const id = String(robot.id)
        seen.add(id)

        const lat = Number(robot.position.latitude)
        const lng = Number(robot.position.longitude)
        const statusType = getRobotStatusType(robot.status)
        const isSelected = id === String(selectedRobotId)
        const radius = isSelected ? 9 : 7

        let marker = markers.get(id)

        if (!marker) {
          marker = L.circleMarker([lat, lng], {
            radius,
            className: `robot-marker ${statusType} ${isSelected ? 'selected' : ''}`.trim(),
            fillOpacity: 0.85,
            opacity: 1,
          })
          marker.bindTooltip(robot.name || `R-${robot.id}`, {
            permanent: false,
            direction: 'top',
          })
          marker.on('click', () => onSelectRobotRef.current?.(robot.id))
          layer.addLayer(marker)
          markers.set(id, marker)
        } else {
          marker.setLatLng([lat, lng])
          marker.setRadius(radius)
        }

        // keep status + selection classes in sync on the rendered element
        const el = marker.getElement()
        if (el) {
          el.setAttribute(
            'class',
            `leaflet-interactive robot-marker ${statusType}${isSelected ? ' selected' : ''}`
          )
        }
      })

    // remove markers whose robot is no longer present
    markers.forEach((marker, id) => {
      if (!seen.has(id)) {
        layer.removeLayer(marker)
        markers.delete(id)
      }
    })
  }, [robots, selectedRobotId])

  useEffect(() => {
    if (!obstacleLayerRef.current) return

    obstacleLayerRef.current.clearLayers()

    obstacles
      .filter(obstacle =>
        Number.isFinite(Number(obstacle.latitude)) &&
        Number.isFinite(Number(obstacle.longitude))
      )
      .forEach(obstacle => {
        const label = obstacle.label || obstacle.name || 'Obstacle'

        // A road may contain several graph segments after junction splitting.
        const segments = Array.isArray(obstacle.segments) ? obstacle.segments : []
        segments.forEach(segment => {
          if (!Array.isArray(segment) || segment.length < 2) return
          const line = L.polyline(segment, {
            color: '#dc2626',
            weight: 5,
            opacity: 0.85,
            lineCap: 'round',
          })
          line.bindTooltip(`Blocked: ${label}`, { direction: 'top', className: 'map-obstacle-tooltip' })
          obstacleLayerRef.current.addLayer(line)
        })

        const marker = L.marker(
          [Number(obstacle.latitude), Number(obstacle.longitude)],
          { icon: OBSTRUCTION_ICON }
        )
        marker.bindTooltip(`Blocked: ${label}`, { direction: 'top', className: 'map-obstacle-tooltip' })
        obstacleLayerRef.current.addLayer(marker)
      })
  }, [obstacles])

  return (
    <section className="map">
      <div className="map-header">
        <div>
          <h2>Live Map</h2>
          <p>
            {isLoadingRoutes
              ? 'Loading task routes...'
              : routeErrorCount > 0
                ? `${routeErrorCount} route${routeErrorCount === 1 ? '' : 's'} failed to load`
                : 'Real-time robot route monitoring'}
          </p>
        </div>
      </div>

      <div className="map-canvas">
        <div ref={mapContainerRef} className='leaflet-map' />
      </div>
    </section>
  )
}

export default LiveMap

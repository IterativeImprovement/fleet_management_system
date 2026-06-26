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
  routesByTaskId = {},
  selectedTaskId,
  selectedRobotId,
  isLoadingRoutes = false,
  routeErrorCount = 0,
  onSelectRobot,
  coloredSegmentsByTaskId = {},
}) {
  const mapContainerRef = useRef(null)
  const mapRef = useRef(null)
  const routeLayerRef = useRef(null)
  const markerLayerRef = useRef(null)
  const obstacleLayerRef = useRef(null)
  const hasFittedAllRoutesRef = useRef(false)

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
    obstacleLayerRef.current = L.layerGroup().addTo(mapRef.current)

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

    Object.values(routesByTaskId || {}).forEach(route => {
      if (!route.coordinates || route.coordinates.length < 2) return

      const isSelected = String(route.taskId) === String(selectedTaskId)
      const coloredSegments = coloredSegmentsByTaskId[route.taskId]

      if (isSelected && coloredSegments && coloredSegments.length > 0) {
        // draw multiple colored polylines for the selected task
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

          routeLayerRef.current.addLayer(line)
          segmentLines.push(line)
          routeLines.push(line)
        })

        if (segmentLines.length > 0) {
          const group = L.featureGroup(segmentLines)
          selectedBounds = group.getBounds()
        }
      } else {
        // draw a single polyline for unselected tasks or while the colored route is loafing 
        const routeLine = L.polyline(route.coordinates, {
          color: isSelected ? '#0a84ff' : '#64748b',
          weight: isSelected ? 6 : 3,
          opacity: isSelected ? 0.95 : 0.45,
          lineCap: 'round',
          lineJoin: 'round',
        })

        routeLine.bindTooltip(`Task ${route.taskId}`, {
          permanent: false,
          direction: 'top',
        })

        routeLayerRef.current.addLayer(routeLine)
        routeLines.push(routeLine)

        if (isSelected) {
          selectedBounds = routeLine.getBounds()
        }
      }
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
  }, [routesByTaskId, selectedTaskId, coloredSegmentsByTaskId])


  useEffect(() => {
    if (!markerLayerRef.current) return

    markerLayerRef.current.clearLayers()

    robots
      .filter(robot =>
        robot.position !== null &&
        Number.isFinite(Number(robot.position.latitude)) &&
        Number.isFinite(Number(robot.position.longitude))
      )
      .forEach(robot => {
        const statusType = getRobotStatusType(robot.status)
        const isSelected = String(robot.id) === String(selectedRobotId)

        const className = `robot-marker ${statusType} ${isSelected ? 'selected' : ''
          }`.trim()

        const marker = L.circleMarker(
          [
            Number(robot.position.latitude),
            Number(robot.position.longitude),
          ],
          {
            radius: isSelected ? 9 : 7,
            className,
            fillOpacity: 0.85,
            opacity: 1,
          }
        )

        marker.bindTooltip(robot.name || `R-${robot.id}`, {
          permanent: false,
          direction: 'top',
        })

        marker.on('click', () => onSelectRobot?.(robot.id))

        markerLayerRef.current.addLayer(marker)
      })
  }, [robots, selectedRobotId, onSelectRobot])

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

        const icon = L.divIcon({
          className: 'map-obstacle-div-icon',
          html: `
            <div class="map-obstacle-marker">
              <span class="obstacle-icon">!</span>
              <span class="obstacle-label">${label}</span>
            </div>
          `,
          iconSize: null,
          iconAnchor: [0, 0],
        })

        const marker = L.marker(
          [Number(obstacle.latitude), Number(obstacle.longitude)],
          { icon }
        )

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

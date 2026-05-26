import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { getRobotStatusType } from '../utils/robotUtils'

function LiveMap({
  robots = [],
  obstacles = [],
  routesByTaskId = {},
  selectedTaskId,
  selectedRobotId,
  isLoadingRoutes = false,
  routeErrorCount = 0,
  onSelectRobot,
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
      center: [1.3521, 103.8198],
      zoom: 12,
      minZoom: 3,
    })

    L.tileLayer('/map/tiles/{z}/{x}/{y}.png', {
      detectRetina: true,
      maxZoom: 19,
      minZoom: 11,
      attribution:
        '<img src="https://www.onemap.gov.sg/web-assets/images/logo/om_logo.png" style="height:20px;width:20px;" />&nbsp;<a href="https://www.onemap.gov.sg/" target="_blank" rel="noopener noreferrer">OneMap</a>&nbsp;&copy;&nbsp;contributors&nbsp;&#124;&nbsp;<a href="https://www.sla.gov.sg/" target="_blank" rel="noopener noreferrer">Singapore Land Authority</a>',
    }).addTo(mapRef.current)

    routeLayerRef.current = L.layerGroup().addTo(mapRef.current)
    markerLayerRef.current = L.layerGroup().addTo(mapRef.current)
    obstacleLayerRef.current = L.layerGroup().addTo(mapRef.current)

    requestAnimationFrame(() => {
      mapRef.current?.invalidateSize()
    })
  }, [])

  useEffect(() => {
    if (!mapContainerRef.current) return

    const resizeMap = () => {
      mapRef.current?.invalidateSize()
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
      mapRef.current?.invalidateSize()
    })
  }, [selectedRobotId, selectedTaskId])

  useEffect(() => {
    if (!routeLayerRef.current) return

    routeLayerRef.current.clearLayers()

    const routeLines = []
    let selectedRouteLine = null

    Object.values(routesByTaskId || {}).forEach(route => {
      if (!route.coordinates || route.coordinates.length < 2) return

      const isSelected = String(route.taskId) === String(selectedTaskId)

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
        selectedRouteLine = routeLine
      }
    })

    if (selectedRouteLine) {
      mapRef.current?.fitBounds(selectedRouteLine.getBounds(), {
        padding: [32, 32],
      })
      return
    }

    if (!hasFittedAllRoutesRef.current && routeLines.length > 0) {
      const routeGroup = L.featureGroup(routeLines)

      mapRef.current?.fitBounds(routeGroup.getBounds(), {
        padding: [32, 32],
      })

      hasFittedAllRoutesRef.current = true
    }
  }, [routesByTaskId, selectedTaskId])

  useEffect(() => {
    if (!markerLayerRef.current) return

    markerLayerRef.current.clearLayers()

    robots
      .filter(robot =>
        Number.isFinite(Number(robot.latitude)) &&
        Number.isFinite(Number(robot.longitude))
      )
      .forEach(robot => {
        const statusType = getRobotStatusType(robot.status)
        const isSelected = robot.id === selectedRobotId

        const className = `robot-marker ${statusType} ${
          isSelected ? 'selected' : ''
        }`.trim()

        const marker = L.circleMarker(
          [Number(robot.latitude), Number(robot.longitude)],
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

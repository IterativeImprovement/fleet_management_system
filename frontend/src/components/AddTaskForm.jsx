import { useEffect, useState } from 'react'
import {
  createCustomLocation,
  CustomLocationConflictError,
  saveSelectedOneMapLocation,
  searchLocations,
} from '../api/locationApi'
import { createTask } from '../utils/taskUtils'

const LOCATION_SEARCH_DEBOUNCE_MS = 350
const MIN_LOCATION_QUERY_LENGTH = 2

const SINGAPORE_BOUNDS = {
  minLatitude: 1.1304,
  maxLatitude: 1.4504,
  minLongitude: 103.6020,
  maxLongitude: 104.0850,
}

function isWithinSingapore(latitude, longitude) {
  return (
    latitude >= SINGAPORE_BOUNDS.minLatitude &&
    latitude <= SINGAPORE_BOUNDS.maxLatitude &&
    longitude >= SINGAPORE_BOUNDS.minLongitude &&
    longitude <= SINGAPORE_BOUNDS.maxLongitude
  )
}

function normaliseDateTime(value) {
  if (!value) return ''
  return value.length === 16 ? `${value}:00` : value
}

function locationToWayPoint(location) {
  return {
    id: location.id,
    latitude: Number(location.latitude),
    longitude: Number(location.longitude),
  }
}

function formatLocationMeta(location) {
  return [
    location.address,
    location.postalCode ? `Singapore ${location.postalCode}` : '',
    location.source,
  ].filter(Boolean).join(' | ')
}

function validateLocationCoordinates(latitude, longitude, label) {
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    throw new Error(`${label} location must have valid latitude and longitude`)
  }

  if (!isWithinSingapore(latitude, longitude)) {
    throw new Error(`${label} location must be within Singapore`)
  }
}

function LocationPicker({
  label,
  query,
  onQueryChange,
  selectedLocation,
  onSelectLocation,
  results,
  onSearch,
  isSearching,
  isCustom,
  onCustomChange,
  customName,
  onCustomNameChange,
  customLatitude,
  onCustomLatitudeChange,
  customLongitude,
  onCustomLongitudeChange,
}) {
  return (
    <section className="location-picker">
      <div className="location-picker-header">
        <label>
          {label} Location
          <div className="location-search-shell">
            <div className="location-search-row">
              <input
                type="search"
                value={query}
                onChange={event => onQueryChange(event.target.value)}
                onKeyDown={event => {
                  if (event.key === 'Enter') {
                    event.preventDefault()
                    onSearch()
                  }
                }}
                placeholder="name / postal"
                disabled={isCustom}
              />

              <button
                type="button"
                className="location-search-button"
                onClick={onSearch}
                disabled={isCustom || isSearching}
              >
                {isSearching ? 'Searching...' : 'Search'}
              </button>
            </div>

            {!isCustom && results.length > 0 && (
              <div className="location-results">
                {results.map(location => (
                  <button
                    type="button"
                    key={`${location.source}-${location.id ?? location.externalId}`}
                    className="location-result"
                    onClick={() => onSelectLocation(location)}
                  >
                    <strong>{location.name}</strong>
                    <span>{formatLocationMeta(location)}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </label>

        {selectedLocation && !isCustom && (
          <div className="selected-location">
            <strong>{selectedLocation.name}</strong>
            <span>{formatLocationMeta(selectedLocation)}</span>
          </div>
        )}

      </div>

      <label className="location-checkbox">
        <input
          type="checkbox"
          checked={isCustom}
          onChange={event => onCustomChange(event.target.checked)}
        />
        Create custom {label.toLowerCase()} location
      </label>

      {isCustom && (
        <div className="custom-location-fields">
          <label>
            Custom Location Name
            <input
              type="text"
              value={customName}
              onChange={event => onCustomNameChange(event.target.value)}
              placeholder="Warehouse entrance"
            />
          </label>

          <div className="coordinate-grid">
            <label>
              Latitude
              <input
                type="number"
                step="any"
                value={customLatitude}
                onChange={event => onCustomLatitudeChange(event.target.value)}
                placeholder="1.2653"
              />
            </label>

            <label>
              Longitude
              <input
                type="number"
                step="any"
                value={customLongitude}
                onChange={event => onCustomLongitudeChange(event.target.value)}
                placeholder="103.8206"
              />
            </label>
          </div>
        </div>
      )}
    </section>
  )
}

function AddTaskForm({ tasks = [], onAddTask, onCancel }) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [type, setType] = useState('STANDARD')
  const [priority, setPriority] = useState(2)
  const [startDateTime, setStartDateTime] = useState('')
  const [completionDateTime, setCompletionDateTime] = useState('')
  const [dependencyId, setDependencyId] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const [startLocationQuery, setStartLocationQuery] = useState('')
  const [endLocationQuery, setEndLocationQuery] = useState('')
  const [startLocationResults, setStartLocationResults] = useState([])
  const [endLocationResults, setEndLocationResults] = useState([])
  const [selectedStartLocation, setSelectedStartLocation] = useState(null)
  const [selectedEndLocation, setSelectedEndLocation] = useState(null)
  const [isSearchingStart, setIsSearchingStart] = useState(false)
  const [isSearchingEnd, setIsSearchingEnd] = useState(false)

  const [isCustomStartLocation, setIsCustomStartLocation] = useState(false)
  const [isCustomEndLocation, setIsCustomEndLocation] = useState(false)
  const [startCustomName, setStartCustomName] = useState('')
  const [startCustomLatitude, setStartCustomLatitude] = useState('')
  const [startCustomLongitude, setStartCustomLongitude] = useState('')
  const [endCustomName, setEndCustomName] = useState('')
  const [endCustomLatitude, setEndCustomLatitude] = useState('')
  const [endCustomLongitude, setEndCustomLongitude] = useState('')

  useEffect(() => {
    const trimmedQuery = startLocationQuery.trim()

    if (
      isCustomStartLocation ||
      trimmedQuery.length < MIN_LOCATION_QUERY_LENGTH ||
      selectedStartLocation?.name === trimmedQuery
    ) {
      setStartLocationResults([])
      setIsSearchingStart(false)
      return
    }

    let isActive = true
    const timeoutId = window.setTimeout(async () => {
      try {
        setIsSearchingStart(true)
        const results = await searchLocations(trimmedQuery)

        if (isActive) {
          setStartLocationResults(results)
          setError('')
        }
      } catch (searchError) {
        if (isActive) {
          setError(searchError.message || 'Failed to search start locations')
        }
      } finally {
        if (isActive) {
          setIsSearchingStart(false)
        }
      }
    }, LOCATION_SEARCH_DEBOUNCE_MS)

    return () => {
      isActive = false
      window.clearTimeout(timeoutId)
    }
  }, [isCustomStartLocation, selectedStartLocation, startLocationQuery])

  useEffect(() => {
    const trimmedQuery = endLocationQuery.trim()

    if (
      isCustomEndLocation ||
      trimmedQuery.length < MIN_LOCATION_QUERY_LENGTH ||
      selectedEndLocation?.name === trimmedQuery
    ) {
      setEndLocationResults([])
      setIsSearchingEnd(false)
      return
    }

    let isActive = true
    const timeoutId = window.setTimeout(async () => {
      try {
        setIsSearchingEnd(true)
        const results = await searchLocations(trimmedQuery)

        if (isActive) {
          setEndLocationResults(results)
          setError('')
        }
      } catch (searchError) {
        if (isActive) {
          setError(searchError.message || 'Failed to search end locations')
        }
      } finally {
        if (isActive) {
          setIsSearchingEnd(false)
        }
      }
    }, LOCATION_SEARCH_DEBOUNCE_MS)

    return () => {
      isActive = false
      window.clearTimeout(timeoutId)
    }
  }, [endLocationQuery, isCustomEndLocation, selectedEndLocation])

  async function handleLocationSearch(role) {
    const isStart = role === 'start'
    const query = isStart ? startLocationQuery : endLocationQuery
    const setResults = isStart ? setStartLocationResults : setEndLocationResults
    const setSearching = isStart ? setIsSearchingStart : setIsSearchingEnd
    const label = isStart ? 'Start' : 'End'

    if (!query.trim()) {
      setError(`${label} location search is required`)
      return
    }

    try {
      setSearching(true)
      setError('')
      const results = await searchLocations(query)
      setResults(results)

      if (results.length === 0) {
        setError(`No ${label.toLowerCase()} locations found`)
      }
    } catch (searchError) {
      setError(searchError.message || `Failed to search ${label.toLowerCase()} locations`)
    } finally {
      setSearching(false)
    }
  }

  async function persistSelectedLocation(location) {
    if (
      String(location.source || '').toUpperCase() === 'ONEMAP' &&
      (location.id === null || location.id === undefined || location.id === '')
    ) {
      return saveSelectedOneMapLocation(location)
    }

    return location
  }

  async function createOrRenameCustomLocation(location, label) {
    try {
      return await createCustomLocation(location)
    } catch (locationError) {
      if (!(locationError instanceof CustomLocationConflictError)) {
        throw locationError
      }

      const existingName = locationError.existingLocation?.name || 'this location'
      const proposedName = locationError.proposedName || location.name
      const shouldRename = window.confirm(
        `A custom ${label.toLowerCase()} location already exists here as "${existingName}". Rename it to "${proposedName}" and use it for this task?`
      )

      if (!shouldRename) {
        throw new Error(`${label} custom location already exists as "${existingName}"`)
      }

      return createCustomLocation(location, { confirmRename: true })
    }
  }

  async function handleSelectStartLocation(location) {
    try {
      setError('')
      const savedLocation = await persistSelectedLocation(location)
      setSelectedStartLocation(savedLocation)
      setStartLocationQuery(savedLocation.name)
      setStartLocationResults([])
    } catch (selectError) {
      setError(selectError.message || 'Failed to save selected start location')
    }
  }

  async function handleSelectEndLocation(location) {
    try {
      setError('')
      const savedLocation = await persistSelectedLocation(location)
      setSelectedEndLocation(savedLocation)
      setEndLocationQuery(savedLocation.name)
      setEndLocationResults([])
    } catch (selectError) {
      setError(selectError.message || 'Failed to save selected end location')
    }
  }

  async function resolveLocation({
    isCustom,
    selectedLocation,
    customName,
    customLatitude,
    customLongitude,
    label,
  }) {
    if (!isCustom) {
      if (!selectedLocation) {
        throw new Error(`${label} location must be selected`)
      }

      if (selectedLocation.id === null || selectedLocation.id === undefined || selectedLocation.id === '') {
        selectedLocation = await persistSelectedLocation(selectedLocation)
      }

      validateLocationCoordinates(
        Number(selectedLocation.latitude),
        Number(selectedLocation.longitude),
        label
      )

      return selectedLocation
    }

    const trimmedName = customName.trim()
    const latitude = Number(customLatitude)
    const longitude = Number(customLongitude)

    if (!trimmedName) {
      throw new Error(`${label} custom location name is required`)
    }

    validateLocationCoordinates(latitude, longitude, label)

    return createOrRenameCustomLocation({
      name: trimmedName,
      latitude,
      longitude,
    }, label)
  }

  function resetForm() {
    setName('')
    setDescription('')
    setType('STANDARD')
    setPriority(2)
    setStartDateTime('')
    setCompletionDateTime('')
    setDependencyId('')
    setStartLocationQuery('')
    setEndLocationQuery('')
    setStartLocationResults([])
    setEndLocationResults([])
    setSelectedStartLocation(null)
    setSelectedEndLocation(null)
    setIsCustomStartLocation(false)
    setIsCustomEndLocation(false)
    setStartCustomName('')
    setStartCustomLatitude('')
    setStartCustomLongitude('')
    setEndCustomName('')
    setEndCustomLatitude('')
    setEndCustomLongitude('')
    setError('')
  }

  async function handleSubmit(event) {
    event.preventDefault()

    const trimmedName = name.trim()
    const trimmedDescription = description.trim()
    const trimmedType = type.trim()

    if (!trimmedName) {
      setError('Task name is required')
      return
    }

    if (!trimmedDescription) {
      setError('Description is required')
      return
    }

    if (!trimmedType) {
      setError('Task type is required')
      return
    }

    if (!startDateTime || !completionDateTime) {
      setError('Start and completion time are required')
      return
    }

    const startTime = new Date(startDateTime)
    const completionTime = new Date(completionDateTime)

    if (completionTime <= startTime) {
      setError('Completion time must be after start time')
      return
    }

    if (completionTime <= new Date()) {
      setError('Completion time must be in the future')
      return
    }

    try {
      setIsSubmitting(true)
      setError('')

      const startLocation = await resolveLocation({
        isCustom: isCustomStartLocation,
        selectedLocation: selectedStartLocation,
        customName: startCustomName,
        customLatitude: startCustomLatitude,
        customLongitude: startCustomLongitude,
        label: 'Start',
      })
      const endLocation = await resolveLocation({
        isCustom: isCustomEndLocation,
        selectedLocation: selectedEndLocation,
        customName: endCustomName,
        customLatitude: endCustomLatitude,
        customLongitude: endCustomLongitude,
        label: 'End',
      })

      const newTask = createTask({
        id: Date.now(),
        name: trimmedName,
        description: trimmedDescription,
        type: trimmedType,
        priority: Number(priority),
        startDateTime: normaliseDateTime(startDateTime),
        completionDateTime: normaliseDateTime(completionDateTime),
        startLocationId: startLocation.id,
        endLocationId: endLocation.id,
        startLocation,
        endLocation,
        startWayPoint: locationToWayPoint(startLocation),
        endWayPoint: locationToWayPoint(endLocation),
        robotId: null,
        dependencyIds: dependencyId ? [Number(dependencyId)] : [],
      })

      await onAddTask(newTask)
      resetForm()
    } catch (submitError) {
      setError(submitError.message || 'Failed to add task')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <form className="add-task-form" onSubmit={handleSubmit}>
      <h3>Add Task</h3>

      <label>
        Task Name
        <input
          type="text"
          value={name}
          onChange={event => {
            setName(event.target.value)
            setError('')
          }}
          placeholder="Tuas shipment"
          required
        />
      </label>

      <label>
        Description
        <input
          type="text"
          value={description}
          onChange={event => {
            setDescription(event.target.value)
            setError('')
          }}
          placeholder="Transport from Tuas to HarbourFront"
          required
        />
      </label>

      <label>
        Type
        <select
          value={type}
          onChange={event => {
            setType(event.target.value)
            setError('')
          }}
          required
        >
          <option value="STANDARD">Standard</option>
          <option value="LARGE">Large</option>
        </select>
      </label>

      <label>
        Priority
        <select
          value={priority}
          onChange={event => setPriority(Number(event.target.value))}
        >
          <option value={1}>High</option>
          <option value={2}>Medium</option>
          <option value={3}>Low</option>
        </select>
      </label>

      <label>
        Start Date Time
        <input
          type="datetime-local"
          value={startDateTime}
          onChange={event => {
            setStartDateTime(event.target.value)
            setError('')
          }}
          required
        />
      </label>

      <label>
        Completion Date Time
        <input
          type="datetime-local"
          value={completionDateTime}
          onChange={event => {
            setCompletionDateTime(event.target.value)
            setError('')
          }}
          required
        />
      </label>

      <LocationPicker
        label="Start"
        query={startLocationQuery}
        onQueryChange={value => {
          setStartLocationQuery(value)
          setSelectedStartLocation(null)
          setError('')
        }}
        selectedLocation={selectedStartLocation}
        onSelectLocation={handleSelectStartLocation}
        results={startLocationResults}
        onSearch={() => handleLocationSearch('start')}
        isSearching={isSearchingStart}
        isCustom={isCustomStartLocation}
        onCustomChange={checked => {
          setIsCustomStartLocation(checked)
          if (checked) {
            setSelectedStartLocation(null)
            setStartLocationResults([])
          }
          setError('')
        }}
        customName={startCustomName}
        onCustomNameChange={value => {
          setStartCustomName(value)
          setError('')
        }}
        customLatitude={startCustomLatitude}
        onCustomLatitudeChange={value => {
          setStartCustomLatitude(value)
          setError('')
        }}
        customLongitude={startCustomLongitude}
        onCustomLongitudeChange={value => {
          setStartCustomLongitude(value)
          setError('')
        }}
      />

      <LocationPicker
        label="End"
        query={endLocationQuery}
        onQueryChange={value => {
          setEndLocationQuery(value)
          setSelectedEndLocation(null)
          setError('')
        }}
        selectedLocation={selectedEndLocation}
        onSelectLocation={handleSelectEndLocation}
        results={endLocationResults}
        onSearch={() => handleLocationSearch('end')}
        isSearching={isSearchingEnd}
        isCustom={isCustomEndLocation}
        onCustomChange={checked => {
          setIsCustomEndLocation(checked)
          if (checked) {
            setSelectedEndLocation(null)
            setEndLocationResults([])
          }
          setError('')
        }}
        customName={endCustomName}
        onCustomNameChange={value => {
          setEndCustomName(value)
          setError('')
        }}
        customLatitude={endCustomLatitude}
        onCustomLatitudeChange={value => {
          setEndCustomLatitude(value)
          setError('')
        }}
        customLongitude={endCustomLongitude}
        onCustomLongitudeChange={value => {
          setEndCustomLongitude(value)
          setError('')
        }}
      />

      <label>
        Dependency
        <select
          value={dependencyId}
          onChange={event => setDependencyId(event.target.value)}
        >
          <option value="">None</option>

          {tasks.map(task => (
            <option key={task.id} value={task.id}>
              {task.id} - {task.name || 'Unnamed task'}
            </option>
          ))}
        </select>
      </label>

      {error && <p className="form-error">{error}</p>}

      <div className="form-actions">
        <button type="button" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </button>

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Adding...' : 'Add Task'}
        </button>
      </div>
    </form>
  )
}

export default AddTaskForm

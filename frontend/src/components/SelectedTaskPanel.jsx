import { useState } from 'react'
import {
  getTaskDeleteBlockReason,
  getPriorityType,
  getTaskStatusLabel,
  getTaskStatusType,
} from '../utils/taskUtils'

function formatWaypoint(wayPoint) {
  if (!wayPoint) return '-'
  return `${wayPoint.latitude}, ${wayPoint.longitude}`
}

function formatLocation(location, wayPoint) {
  if (!location) return formatWaypoint(wayPoint)

  return `${location.name} (${formatWaypoint(location)})`
}

function formatDateTime(dateTime) {
  if (!dateTime) return '-'
  return dateTime.replace('T', ' ')
}

function formatDependency(dependencyId, tasks) {
  const dependency = tasks.find(
    task => String(task.id) === String(dependencyId)
  )

  return dependency?.name || `Task ${dependencyId}`
}

function SelectedTaskPanel({
  task,
  tasks = [],
  assignedRobot,
  onBack,
  onViewRobot,
  onDeleteTask,
}) {
  const [deleteError, setDeleteError] = useState('')
  const [isDeleting, setIsDeleting] = useState(false)
  const statusType = getTaskStatusType(task.status)
  const priorityType = getPriorityType(task.priority)
  const dependencies = task.dependencyIds || []
  const deleteBlockReason = getTaskDeleteBlockReason(task, tasks)
  const deleteMessage = deleteError || deleteBlockReason

  async function handleDeleteTask() {
    if (deleteBlockReason) {
      setDeleteError(deleteBlockReason)
      return
    }

    const taskName = task.name || `Task ${task.id}`
    const shouldDelete = window.confirm(
      `Delete ${taskName}? This action cannot be undone.`
    )

    if (!shouldDelete) return

    try {
      setIsDeleting(true)
      setDeleteError('')
      await onDeleteTask?.(task.id)
    } catch (error) {
      setDeleteError(error.message || 'Failed to delete task')
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <div className="selected-task-panel">
      <button className="back-button" onClick={onBack}>
        ← Back to Tasks
      </button>

      <h3>Selected Task</h3>

      <div className="selected-task-card">
        <div className="selected-task-header">
          <div>
            <h2>{task.name || `Task ${task.id}`}</h2>
            <p className="selected-task-subtitle">
              {task.type || 'No task type'}
            </p>
          </div>

          <span className={`task-priority ${priorityType}`}>
            {priorityType}
          </span>
        </div>

        <div className="task-detail-row">
          <span>Task ID:</span>
          <strong>{task.id}</strong>
        </div>

        {task.isLocalOnly && (
          <div className="task-sync-warning">
            This task is only saved in the frontend. Backend create failed
            {task.syncError ? `: ${task.syncError}` : '.'}
          </div>
        )}

        <div className="task-detail-row">
          <span>Status:</span>
          <strong>
            <span className={`task-status-pill ${statusType}`}>
              {getTaskStatusLabel(task.status)}
            </span>
          </strong>
        </div>

        <div className="task-detail-row">
          <span>Description:</span>
          <strong>{task.description || '-'}</strong>
        </div>

        <div className="task-detail-row">
          <span>Start Time:</span>
          <strong>{formatDateTime(task.startDateTime)}</strong>
        </div>

        <div className="task-detail-row">
          <span>Completion Time:</span>
          <strong>{formatDateTime(task.completionDateTime)}</strong>
        </div>

        <div className="task-detail-row">
          <span>Start Location:</span>
          <strong>{formatLocation(task.startLocation, task.startWayPoint)}</strong>
        </div>

        <div className="task-detail-row">
          <span>End Location:</span>
          <strong>{formatLocation(task.endLocation, task.endWayPoint)}</strong>
        </div>

        <div className="task-detail-row">
          <span>Assigned Robot:</span>
          <strong>
            {assignedRobot
              ? assignedRobot.name || `Robot ${assignedRobot.id}`
              : 'Unassigned'}
          </strong>
        </div>

        <div className="task-detail-row">
          <span>Dependencies:</span>
          <strong>
            {dependencies.length > 0
              ? dependencies.map(id => formatDependency(id, tasks)).join(', ')
              : 'None'}
          </strong>
        </div>

        {assignedRobot && (
          <button
            type="button"
            className="primary-action"
            onClick={() => onViewRobot(assignedRobot.id)}
          >
            View Assigned Robot
          </button>
        )}

        <button
          type="button"
          className="delete-task-button"
          onClick={handleDeleteTask}
          disabled={isDeleting || Boolean(deleteBlockReason)}
        >
          {isDeleting ? 'Deleting...' : 'Delete Task'}
        </button>

        {deleteMessage && (
          <p className="task-delete-error">{deleteMessage}</p>
        )}
      </div>
    </div>
  )
}

export default SelectedTaskPanel

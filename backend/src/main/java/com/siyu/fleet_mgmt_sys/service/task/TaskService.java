package com.siyu.fleet_mgmt_sys.service.task;

import com.siyu.fleet_mgmt_sys.dto.task.TaskRequestDTO;
import com.siyu.fleet_mgmt_sys.dto.task.TaskResponseDTO;
import com.siyu.fleet_mgmt_sys.exception.notfoundexception.TaskNotFoundException;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.model.Location;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskType;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.repository.WayPointRepository;
import com.siyu.fleet_mgmt_sys.service.RobotRepairService;
import com.siyu.fleet_mgmt_sys.service.task.allocation.TaskAllocationService;
import com.siyu.fleet_mgmt_sys.service.location.LocationService;
import com.siyu.fleet_mgmt_sys.service.task.submission.TaskClusterService;
import com.siyu.fleet_mgmt_sys.service.task.submission.TaskSubmissionPipeline;
import com.siyu.fleet_mgmt_sys.specification.TaskSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final WayPointRepository wayPointRepository;
    private final RobotRepository robotRepository;

    private final TaskSubmissionPipeline taskSubmissionPipeline;
    private final TaskDependencyService dependencyService;
    private final TaskClusterService clusterService;
    private final TaskAllocationService allocationService;
    private final LocationService locationService;

    private final RobotRepairService robotRepairService;

    @Transactional
    public TaskResponseDTO createTask(TaskRequestDTO req) {
        validateCreateRequest(req);

        ResolvedWaypoint resolvedStart = resolveWaypoint(req.getStartLocationId(), req.getStartWayPointStr());
        ResolvedWaypoint resolvedEnd = resolveWaypoint(req.getEndLocationId(), req.getEndWayPointStr());
        WayPoint start = resolvedStart.wayPoint();
        WayPoint end = resolvedEnd.wayPoint();

        if (!isWithinSingapore(start.getLatitude(), start.getLongitude())
                || !isWithinSingapore(end.getLatitude(), end.getLongitude())) {
            throw new IllegalArgumentException("Waypoints not within Singapore bounds.");
        }

        TaskType taskType = toTaskType(req.getType());
        List<Long> dependencyIds = req.getDependencyIds() == null
                ? List.of()
                : req.getDependencyIds().stream().distinct().toList();
        if (dependencyIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("Dependency IDs cannot contain null values.");
        }
        List<Task> dependencies = dependencyIds.stream()
                .map(depId -> taskRepository.findById(depId)
                        .orElseThrow(() -> new TaskNotFoundException(depId)))
                .toList();

        WayPoint savedStart = wayPointRepository.save(start);
        WayPoint savedEnd = wayPointRepository.save(end);

        Task task = new Task();
        task.setName(req.getName().trim());
        task.setDescription(req.getDescription().trim());
        task.setPriority(req.getPriority());
        task.setStartWayPoint(savedStart);
        task.setEndWayPoint(savedEnd);
        task.setStartLocation(resolvedStart.location());
        task.setEndLocation(resolvedEnd.location());
        task.setStartDateTime(req.getStartDateTime());
        task.setCompletionDateTime(req.getCompletionDateTime());
        task.setType(taskType);
        task.setDependencies(dependencies);

        if (req.getSimulationId() != null) {
            task.setSimulated(true);
            task.setSimulationId(req.getSimulationId());
        }

        Task savedTask = taskSubmissionPipeline.submitTask(task);
        System.out.println("Task created successfully!\n" + savedTask.toString());
        return new TaskResponseDTO(savedTask);
    }

    @Transactional(readOnly = true)
    public TaskResponseDTO getTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id)); // when no tasks matches the given id
        System.out.println("Task retrieved successfully!\n" + task);
        return new TaskResponseDTO(task);
    }

    @Transactional
    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        String taskString = task.toString();
        WayPoint startWayPoint = task.getStartWayPoint();
        WayPoint endWayPoint = task.getEndWayPoint();

        clusterService.removeTaskFromClusterCaches(task);
        taskRepository.delete(task);
        taskRepository.flush();
        wayPointRepository.deleteAll(List.of(startWayPoint, endWayPoint));
        wayPointRepository.flush();

        System.out.println("Task deleted successfully!\n" + taskString);
    }

    public TaskResponseDTO updateTask(Long id, TaskRequestDTO req) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        if (req.getName() != null) task.setName(req.getName());
        if (req.getDescription() != null) task.setDescription(req.getDescription());
        TaskType taskType = req.getType() != null ? TaskType.valueOf(req.getType().toUpperCase()) : null; // convert to Tasktype
        if (req.getType() != null) task.setType(taskType);
        if (req.getStatus() != null) task.setStatus(TaskStatus.valueOf(req.getStatus().toUpperCase()));
        if (req.getPriority() != null) task.setPriority(req.getPriority());
        if (req.getStartDateTime() != null) task.setStartDateTime(req.getStartDateTime());
        if (req.getCompletionDateTime() != null) task.setCompletionDateTime(req.getCompletionDateTime());

        if (req.getStartLocationId() != null || req.getStartWayPointStr() != null) {
            ResolvedWaypoint resolvedStart = resolveWaypoint(req.getStartLocationId(), req.getStartWayPointStr());
            WayPoint start = wayPointRepository.save(resolvedStart.wayPoint());
            task.setStartWayPoint(start);
            task.setStartLocation(resolvedStart.location());
        }

        if (req.getEndLocationId() != null || req.getEndWayPointStr() != null) {
            ResolvedWaypoint resolvedEnd = resolveWaypoint(req.getEndLocationId(), req.getEndWayPointStr());
            WayPoint end = wayPointRepository.save(resolvedEnd.wayPoint());
            task.setEndWayPoint(end);
            task.setEndLocation(resolvedEnd.location());
        }

        if (req.getDependencyIds() != null && !req.getDependencyIds().isEmpty()) {
            List<Task> dependencies = req.getDependencyIds().stream()
                    .map(depId -> taskRepository.findById(depId)
                            .orElseThrow(() -> new TaskNotFoundException(depId)))
                    .toList();
            task.setDependencies(dependencies);
        }

        return new TaskResponseDTO(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponseDTO> filterTasks(Integer priority, String type, String status, String timeLeft,
                                             String startDateTime, String completionDateTime) {
        List<Task> tasks = taskRepository.findAll(
                TaskSpecification.filter(priority, type, status, timeLeft, startDateTime, completionDateTime)
        );

        System.out.println("Retrieved filtered tasks:" + tasks);
        return tasks.stream()
                .map(TaskResponseDTO::new)
                .toList();
    }

    @Transactional
    public void completeTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        // unlink robot and task from each other
        Robot robot = task.getRobot();
        task.setStatus(TaskStatus.COMPLETED);
        task.setRobot(null);
        taskRepository.save(task);

        // release any tasks that were waiting on this one
        dependencyService.releaseUnblockedTasks(task);

        // refresh cluster cache
        if (task.getEndCluster() != null) {
            clusterService.refreshTopTasks(task.getEndCluster().getId());
        }

        // a task may have no assigned robot (e.g. completed straight from the pool)
        if (robot == null) {
            return;
        }

        robot.getTasks().remove(task);

        if (task.getName().startsWith("BREAKDOWN")) {
            robotRepairService.startRepair(task, robot);
            return;
        }

        if (robot.getTasks().isEmpty()) {
            robot.setStatus(RobotStatus.IDLE);
            robotRepository.save(robot);
            allocationService.assignNextTask(robot);
        } else {
            // assign highest priority remaining task
            robot.getTasks().stream()
                    .max(Comparator.comparingDouble(t -> t.getPriorityFor(robot.getType())))
                    .ifPresent(next -> allocationService.assign(robot, next, true));
            robotRepository.save(robot);
        }

    }

    public boolean isWithinSingapore(double lat, double lon) {
        return lat >= 1.1304 && lat <= 1.4504
                && lon >= 103.6020 && lon <= 104.0850;
    }

    private void validateCreateRequest(TaskRequestDTO req) {
        if (req == null) {
            throw new IllegalArgumentException("Task request is required.");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("Task name is required.");
        }
        if (req.getDescription() == null || req.getDescription().isBlank()) {
            throw new IllegalArgumentException("Task description is required.");
        }
        if (req.getPriority() == null || req.getPriority() < 1 || req.getPriority() > 3) {
            throw new IllegalArgumentException("Task priority must be between 1 and 3.");
        }
        boolean hasStartLocation = req.getStartLocationId() != null;
        boolean hasEndLocation = req.getEndLocationId() != null;
        boolean hasStartWayPoint = req.getStartWayPointStr() != null && !req.getStartWayPointStr().isBlank();
        boolean hasEndWayPoint = req.getEndWayPointStr() != null && !req.getEndWayPointStr().isBlank();

        if (!hasStartLocation && !hasStartWayPoint) {
            throw new IllegalArgumentException("Start location is required.");
        }
        if (!hasEndLocation && !hasEndWayPoint) {
            throw new IllegalArgumentException("End location is required.");
        }
        if (hasStartLocation && hasStartWayPoint) {
            throw new IllegalArgumentException("Provide either start location id or start waypoint, not both.");
        }
        if (hasEndLocation && hasEndWayPoint) {
            throw new IllegalArgumentException("Provide either end location id or end waypoint, not both.");
        }
        if (req.getStartDateTime() == null || req.getCompletionDateTime() == null) {
            throw new IllegalArgumentException("Start and completion times are required.");
        }
        if (!req.getCompletionDateTime().isAfter(req.getStartDateTime())) {
            throw new IllegalArgumentException("Completion time must be after start time.");
        }
        if (!req.getCompletionDateTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Completion time must be in the future.");
        }
    }

    private TaskType toTaskType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Task type is required.");
        }

        return switch (type.trim().toUpperCase()) {
            case "STANDARD", "STANDARDTRANSPORT" -> TaskType.STANDARD;
            case "LARGE" -> TaskType.LARGE;
            default -> throw new IllegalArgumentException("Unknown task type: " + type);
        };
    }

    private ResolvedWaypoint resolveWaypoint(Long locationId, String wayPointStr) {
        if (locationId != null) {
            Location location = locationService.getLocationOrThrow(locationId);
            return new ResolvedWaypoint(
                    location,
                    new WayPoint(location.getLatitude(), location.getLongitude())
            );
        }

        if (wayPointStr == null || wayPointStr.isBlank()) {
            throw new IllegalArgumentException("Waypoint is required.");
        }

        return new ResolvedWaypoint(null, new WayPoint(wayPointStr));
    }

    private record ResolvedWaypoint(Location location, WayPoint wayPoint) {
    }

}

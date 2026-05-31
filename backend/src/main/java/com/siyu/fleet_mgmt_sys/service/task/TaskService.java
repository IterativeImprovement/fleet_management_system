package com.siyu.fleet_mgmt_sys.service.task;

import com.siyu.fleet_mgmt_sys.dto.TaskRequestDTO;
import com.siyu.fleet_mgmt_sys.dto.TaskResponseDTO;
import com.siyu.fleet_mgmt_sys.exception.TaskNotFoundException;
import com.siyu.fleet_mgmt_sys.model.Task;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskType;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.repository.WayPointRepository;
import com.siyu.fleet_mgmt_sys.service.task.allocation.TaskAllocationService;
import com.siyu.fleet_mgmt_sys.service.task.submission.TaskClusterService;
import com.siyu.fleet_mgmt_sys.service.task.submission.TaskSubmissionPipeline;
import com.siyu.fleet_mgmt_sys.specification.TaskSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public TaskResponseDTO createTask(TaskRequestDTO req) {
        WayPoint start = wayPointRepository.save(new WayPoint(req.getStartWayPointStr()));
        WayPoint end = wayPointRepository.save(new WayPoint((req.getEndWayPointStr())));

        Task task = new Task();
        task.setName(req.getName());
        task.setDescription(req.getDescription());
        task.setPriority(req.getPriority());
        task.setStartWayPoint(start);
        task.setEndWayPoint(end);
        task.setStartDateTime(req.getStartDateTime());
        task.setCompletionDateTime(req.getCompletionDateTime());
        TaskType taskType = req.getType() != null ? TaskType.valueOf(req.getType().toUpperCase()) : null; // convert to Tasktype
        task.setType(taskType);
        task.setDependencies(
                req.getDependencyIds().stream()
                        .map(depId -> taskRepository.findById(depId)
                                .orElseThrow(() -> new TaskNotFoundException(depId)))
                        .toList()
        );

        Task savedTask = taskSubmissionPipeline.submitTask(task);
        System.out.println("Task created successfully!\n" + savedTask.toStringDetailed());
        return new TaskResponseDTO(savedTask);
    }

    @Transactional(readOnly = true)
    public TaskResponseDTO getTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id)); // when no tasks matches the given id
        System.out.println("Task retrieved successfully! id=" + task.getId() + ", name=" + task.getName());
        return new TaskResponseDTO(task);
    }

    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        String taskString = task.toString();
        taskRepository.delete(task);

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

        if (req.getStartWayPointStr() != null) {
            WayPoint start = wayPointRepository.save(new WayPoint(req.getStartWayPointStr()));
            task.setStartWayPoint(start);
        }

        if (req.getEndWayPointStr() != null) {
            WayPoint end = wayPointRepository.save(new WayPoint(req.getEndWayPointStr()));
            task.setEndWayPoint(end);
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

    public void completeTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        // unlink robot and task from each other
        Robot robot = task.getRobot();
        task.setStatus(TaskStatus.COMPLETED);
        task.setRobot(null);
        taskRepository.save(task);

        robot.getTasks().remove(task);

        // release any tasks that were waiting on this one
        dependencyService.releaseUnblockedTasks(task);

        // refresh cluster cache
        clusterService.refreshTopTasks(task.getEndCluster().getId());

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

}

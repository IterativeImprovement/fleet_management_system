package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.dto.TaskRequestDTO;
import com.siyu.fleet_mgmt_sys.exception.TaskNotFoundException;
import com.siyu.fleet_mgmt_sys.model.Task;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.repository.WayPointRepository;
import com.siyu.fleet_mgmt_sys.specification.TaskSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final WayPointRepository wayPointRepository;

    public Task createTask(TaskRequestDTO req) {
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
        task.setType(req.getType());
        task.setTasks(req.getTasks());

        System.out.println("Task created successfully!\n" + task.toStringDetailed());
        return taskRepository.save(task);
    }

    public Task getTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id)); // when no tasks matches the given id
        System.out.println("Task retrieved successfully!\n" + task.toStringDetailed());
        return task;
    }

    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        String taskString = task.toString();
        taskRepository.delete(task);

        System.out.println("Task deleted successfully!\n" + taskString);
    }

    public List<Task> filterTasks(Integer priority, String type, String timeLeft,
                                  String startDateTime, String completionDateTime) {
        List<Task> tasks = taskRepository.findAll(
                TaskSpecification.filter(priority, type, timeLeft, startDateTime, completionDateTime)
        );

        System.out.println("Retrieved filtered tasks:" + tasks);
        return tasks;
    }

}

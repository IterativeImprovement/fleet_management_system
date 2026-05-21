package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.TaskRequestDTO;
import com.siyu.fleet_mgmt_sys.model.Task;
import com.siyu.fleet_mgmt_sys.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody TaskRequestDTO req) {
        Task result = taskService.createTask(req);
        return ResponseEntity.created(URI.create("/task/" + result.getId()))
                .body(result);
    }

    /*
    POST
    http://localhost:8080/task

    //header
    Content-Type: application/json

    //body
    {
        "name": "Deliver package",
            "description": "Deliver package to warehouse A",
            "priority": 1,
            "type": "delivery",
            "startWayPointStr": "1.3081,103.8551",
            "endWayPointStr": "1.2739,103.8012",
            "startDateTime": "2026-05-21T14:49:33",
            "completionDateTime": "2026-05-21T16:00:00"
    }
    */

    @GetMapping("/{id}") // GET localhost:8080/task/id
    public ResponseEntity<Task> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTask(id));
    }

    @DeleteMapping("/{id}") // DELETE localhost:8080/task/id
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping // GET localhost:8080/task?startDateTime=after:2026-05-21T14:49:33
    public ResponseEntity<List<Task>> filterTask(
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String timeLeft, // gte:Dxx:xx:xx:xx (days, hours, minutes, seconds)
            @RequestParam(required = false) String startDateTime, // after:D2026-05-21T14:49:33 / before / on
            @RequestParam(required = false) String completionDateTime
    ) // 2026-05-21T14:49:33
    {
        return ResponseEntity.ok(taskService.filterTasks(priority, type, timeLeft, startDateTime, completionDateTime));
    }
}
package com.siyu.fleet_mgmt_sys.service.simulation;

import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.SimulationRunRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.repository.WayPointRepository;
import com.siyu.fleet_mgmt_sys.service.dispatch.DispatchService;
import com.siyu.fleet_mgmt_sys.service.task.submission.TaskClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationResetService {

    private final RobotRepository robotRepository;
    private final TaskRepository taskRepository;
    private final WayPointRepository wayPointRepository;
    private final TaskClusterService clusterService;
    private final SimulationRunRepository simulationRunRepository;
    private final DispatchService dispatchService;

    @Transactional
    public void reset(Long simulationId) {
        log.info("Resetting simulation id={}", simulationId);

        dispatchService.clear(simulationId);
        List<Long> simRobotIds = robotRepository.findAllSimulationIds(simulationId);
        List<Robot> simRobots = robotRepository.findAllById(simRobotIds);
        List<Task> simTasks = taskRepository.findBySimulationId(simulationId);

        deleteSimData(simRobots, simTasks);

        if (simulationRunRepository.existsById(simulationId)) {
            simulationRunRepository.deleteById(simulationId);
        }
        log.info("Reset complete for simulation id={}", simulationId);
    }

    /**
     * Removes ALL simulation robots, tasks and runs across every run.
     * Called before generating a fresh simulation so leftover data from
     * previous runs does
     * not accumulate in the database.
     */
    @Transactional
    public void resetAll() {
        log.info("Resetting all simulation data");

        dispatchService.clearAll();
        List<Long> simRobotIds = robotRepository.findAllSimulationIds();
        List<Robot> simRobots = robotRepository.findAllById(simRobotIds);
        List<Task> simTasks = taskRepository.findBySimulationIdNotNull();

        deleteSimData(simRobots, simTasks);

        simulationRunRepository.deleteAll();
        log.info("Reset complete for all simulation data");
    }

    private void deleteSimData(List<Robot> simRobots, List<Task> simTasks) {
        if (!simTasks.isEmpty()) {
            List<Long> simTaskIds = simTasks.stream().map(Task::getId).toList();

            // Remove sim tasks from the dependency_id side of the junction table
            taskRepository.deleteInverseTaskDependencies(simTaskIds);

            // Clear the owning side
            for (Task task : simTasks) {
                task.getDependencies().clear();
            }
            taskRepository.saveAll(simTasks);
            taskRepository.flush();

            // Unlink tasks from their robots
            for (Robot robot : simRobots) {
                robot.getTasks().clear();
            }
            robotRepository.saveAll(simRobots);
            robotRepository.flush();

            // Re-fetch tasks now that dependencies are cleared
            simTasks = taskRepository.findAllById(simTaskIds);

            List<WayPoint> waypoints = new ArrayList<>();
            for (Task task : simTasks) {
                if (task.getStartWayPoint() != null)
                    waypoints.add(task.getStartWayPoint());
                if (task.getEndWayPoint() != null)
                    waypoints.add(task.getEndWayPoint());
            }
            clusterService.removeTasksFromClusterCaches(simTasks);

            taskRepository.deleteAll(simTasks);
            taskRepository.flush();
            wayPointRepository.deleteAll(waypoints);
            wayPointRepository.flush();

            log.info("Deleted {} simulation tasks", simTaskIds.size());
        }

        robotRepository.deleteAll(simRobots);
        log.info("Deleted {} simulation robots", simRobots.size());
    }
}

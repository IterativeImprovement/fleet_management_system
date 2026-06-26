package com.siyu.fleet_mgmt_sys.service.simulation;

import com.siyu.fleet_mgmt_sys.dto.simulation.SimulationConfig;
import com.siyu.fleet_mgmt_sys.model.simulation.SimulationResult;
import com.siyu.fleet_mgmt_sys.model.simulation.SimulationEvent;
import com.siyu.fleet_mgmt_sys.model.enums.SimulationEventType;
import com.siyu.fleet_mgmt_sys.model.enums.TaskType;
import com.siyu.fleet_mgmt_sys.repository.RoadSegmentRepository;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.WayPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a deterministic SimulationResult from a given seed.
 * The same seed will always produce the same set of events.
 */
@Service
@RequiredArgsConstructor
public class SimulationEngine {

    private final WayPointRepository waypointRepository;
    private final RobotRepository robotRepository;
    private final RoadSegmentRepository roadSegmentRepository;

    public SimulationResult generate(SimulationConfig config) {

        // Fetch reference IDs from DB once, upfront
        List<Long> waypointIds = waypointRepository.findAllIds();
        List<Long> robotIds = robotRepository.findAllIds();
        List<Long> roadSegmentIds = roadSegmentRepository.findAllIds();

        // Seed independent RNG streams — each stream is isolated from the others
        Random taskRng        = new Random(config.getSeed() ^ 0xAAAA_AAAAL);
        Random malfunctionRng = new Random(config.getSeed() ^ 0xBBBB_BBBBL);
        Random obstructionRng = new Random(config.getSeed() ^ 0xCCCC_CCCCL);

        // Generate task events first — dependency resolution requires them to exist
        List<SimulationEvent> taskEvents = generateTaskEvents(config, taskRng, waypointIds);

        List<SimulationEvent> allEvents = new ArrayList<>();
        allEvents.addAll(taskEvents);
        allEvents.addAll(generateMalfunctionEvents(config, malfunctionRng, robotIds));
        allEvents.addAll(generateObstructionEvents(config, obstructionRng, roadSegmentIds));

        // Sort all events by simTime
        allEvents.sort(Comparator.comparingDouble(SimulationEvent::getSimTime));

        // Assign final sequential IDs after sorting
        for (int i = 0; i < allEvents.size(); i++) {
            allEvents.get(i).setEventId((long) (i + 1));
        }

        return SimulationResult.builder()
                .seed(config.getSeed())
                .config(config)
                .events(allEvents)
                .build();
    }

    private List<SimulationEvent> generateTaskEvents(SimulationConfig config, Random rng, List<Long> waypointIds) {
        List<SimulationEvent> events = new ArrayList<>();
        Map<Integer, List<Integer>> dependencyIndices = new HashMap<>();

        TaskType[] pickableTypes = { TaskType.STANDARD, TaskType.LARGE };
        double t = 0;
        int counter = 0;

        while (true) {
            t += nextExponential(rng, config.getTaskArrivalRatePerSecond());
            if (t > config.getDurationSeconds()) break;

            // Pick distinct start and end waypoints
            Long start = pickOne(waypointIds, rng);
            Long end;
            do { end = pickOne(waypointIds, rng); } while (end.equals(start));

            TaskType type = pickableTypes[rng.nextInt(pickableTypes.length)];
            int priority = rng.nextInt(5) + 1;

            // Completion deadline: simTime + random offset between min and max
            double range = config.getMaxTaskCompletionSeconds() - config.getMinTaskCompletionSeconds();
            double completionDeadline = t + config.getMinTaskCompletionSeconds() + (rng.nextDouble() * range);

            // Dependency selection: pick from N most recent tasks by index
            if (!events.isEmpty() && rng.nextDouble() < config.getDependentTaskProbability()) {
                int poolSize = Math.min(events.size(), config.getDependencyPoolSize());
                int startIndex = events.size() - poolSize;

                // Build index pool from recent tasks
                List<Integer> poolIndices = new ArrayList<>();
                for (int i = startIndex; i < events.size(); i++) poolIndices.add(i);

                // Fisher-Yates shuffle — deterministic with seeded RNG
                for (int i = poolIndices.size() - 1; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    int tmp = poolIndices.get(i);
                    poolIndices.set(i, poolIndices.get(j));
                    poolIndices.set(j, tmp);
                }

                int numDeps = Math.min(rng.nextInt(config.getMaxDependentTasks()) + 1, poolIndices.size());
                dependencyIndices.put(counter, new ArrayList<>(poolIndices.subList(0, numDeps)));
            }

            SimulationEvent event = new SimulationEvent();
            event.setEventType(SimulationEventType.TASK_CREATED);
            event.setSimTime(t);
            event.setTaskName("SIM-TASK-" + String.format("%03d", counter + 1));
            event.setTaskDescription("Simulated " + type + " task");
            event.setTaskPriority(priority);
            event.setTaskType(type);
            event.setStartWaypointId(start);
            event.setEndWaypointId(end);
            event.setCompletionDeadline(completionDeadline);
            event.setDependencyEventIds(new ArrayList<>());

            events.add(event);
            counter++;
        }

        // Resolve dependency indices to position-based IDs (1-indexed)
        // These are temporary — final IDs are assigned after sorting in generate()
        for (Map.Entry<Integer, List<Integer>> entry : dependencyIndices.entrySet()) {
            SimulationEvent dependent = events.get(entry.getKey());
            List<Long> depIds = entry.getValue().stream()
                    .map(idx -> (long) (idx + 1))
                    .collect(Collectors.toList());
            dependent.setDependencyEventIds(depIds);
        }

        return events;
    }

    private List<SimulationEvent> generateMalfunctionEvents(SimulationConfig config, Random rng, List<Long> robotIds) {
        List<SimulationEvent> events = new ArrayList<>();

        for (Long robotId : robotIds) {
            double t = 0;

            while (true) {
                t += nextExponential(rng, config.getMalfunctionRatePerRobotPerSecond());
                if (t > config.getDurationSeconds()) break;

                SimulationEvent event = new SimulationEvent();
                event.setEventType(SimulationEventType.ROBOT_MALFUNCTION);
                event.setSimTime(t);
                event.setRobotId(robotId);

                events.add(event);
            }
        }

        return events;
    }

    private List<SimulationEvent> generateObstructionEvents(SimulationConfig config, Random rng, List<Long> roadSegmentIds) {
        List<SimulationEvent> events = new ArrayList<>();
        double t = 0;

        while (true) {
            t += nextExponential(rng, config.getRouteObstructionRatePerSecond());
            if (t > config.getDurationSeconds()) break;

            SimulationEvent event = new SimulationEvent();
            event.setEventType(SimulationEventType.ROUTE_OBSTRUCTION);
            event.setSimTime(t);
            event.setRoadSegmentId(pickOne(roadSegmentIds, rng));

            events.add(event);
        }

        return events;
    }

    private double nextExponential(Random rng, double rate) {
        if (rate <= 0) throw new IllegalArgumentException("Rate must be positive, got: " + rate);
        double u;
        do { u = rng.nextDouble(); } while (u == 0.0);
        return -Math.log(u) / rate;
    }

    private <T> T pickOne(List<T> list, Random rng) {
        return list.get(rng.nextInt(list.size()));
    }
}
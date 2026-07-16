package com.siyu.fleet_mgmt_sys.service.simulation;

import com.siyu.fleet_mgmt_sys.dto.simulation.SimulationConfig;
import com.siyu.fleet_mgmt_sys.model.enums.LocationSource;
import com.siyu.fleet_mgmt_sys.model.enums.SimulationEventType;
import com.siyu.fleet_mgmt_sys.model.enums.TaskType;
import com.siyu.fleet_mgmt_sys.model.simulation.SimulationEvent;
import com.siyu.fleet_mgmt_sys.model.simulation.SimulationResult;
import com.siyu.fleet_mgmt_sys.model.simulation.SimulationRun;
import com.siyu.fleet_mgmt_sys.repository.LocationRepository;
import com.siyu.fleet_mgmt_sys.repository.RoadRepository;
import com.siyu.fleet_mgmt_sys.repository.SimulationRunRepository;
import com.siyu.fleet_mgmt_sys.service.external.OneMapService;
import com.siyu.fleet_mgmt_sys.service.external.LTAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a deterministic SimulationResult from a given seed.
 * The same seed will always produce the same set of events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationEngine {

    private final LocationRepository locationRepository;
    private final RoadRepository roadRepository;
    private final SimulationRunRepository simulationRunRepository;
    private final SimulationRobotSeeder simulationRobotSeeder;
    private final OneMapService oneMapService;
    private final LTAService LTAService;

    public SimulationResult generate(SimulationConfig config) {
        // Ensure locations are populated before generation
        oneMapService.populateIfEmpty();

        // Ensure roads are populated before generation
        LTAService.populateIfEmpty(); // this method also saves the speed bands

        // Step 1: Get simulation ID (sequential)
        SimulationRun run = new SimulationRun();
        run.setSeed(config.getSeed());
        run = simulationRunRepository.save(run);
        Long simulationId = run.getId();
        log.info("Created simulation run with id={}, seed={}", simulationId, config.getSeed());

        // Step 2: Create and save simulation robots
        List<Long> robotIds = simulationRobotSeeder.seed(config, simulationId);
        log.info("Seeded {} robots for simulationId={}", robotIds.size(), simulationId);

        // Step 3: Fetch location and roads from DB
        List<Long> locationIds = locationRepository.findAllIdsBySource(LocationSource.ONEMAP);
        List<Long> roadIds = roadRepository.findAllIds();

        log.info("Simulation reference data: locations={}, robots={}, roadSegments={}",
                locationIds.size(), robotIds.size(), roadIds.size());

        // Error logs
        if (locationIds.isEmpty()) {
            log.error("No locations found in database - task events cannot be generated");
            throw new IllegalStateException("No locations found in database. Please populate locations before running a simulation.");
        }
        if (robotIds.isEmpty()) {
            log.warn("No robots seeded. Malfunction events will not be generated");
        }
        if (roadIds.isEmpty()) {
            log.warn("No road segments found in database. Obstruction events will not be generated");
        }

        // Step 4: Seed independent RNG streams
        Random taskRng        = new Random(config.getSeed() ^ 0xAAAA_AAAAL);
        Random malfunctionRng = new Random(config.getSeed() ^ 0xBBBB_BBBBL);
        Random obstructionRng = new Random(config.getSeed() ^ 0xCCCC_CCCCL);

        // Step 5: Generate events
        List<SimulationEvent> taskEvents = generateTaskEvents(config, taskRng, locationIds);
        log.info("Generated {} task events", taskEvents.size());

        List<SimulationEvent> allEvents = new ArrayList<>(taskEvents);

        if (!robotIds.isEmpty()) {
            List<SimulationEvent> malfunctionEvents = generateMalfunctionEvents(config, malfunctionRng, robotIds);
            log.info("Generated {} malfunction events", malfunctionEvents.size());
            allEvents.addAll(malfunctionEvents);
        }

        if (!roadIds.isEmpty()) {
            List<SimulationEvent> obstructionEvents = generateObstructionEvents(config, obstructionRng, roadIds);
            log.info("Generated {} obstruction events", obstructionEvents.size());
            allEvents.addAll(obstructionEvents);
        }

        // Step 6: Sort all events by simTime
        allEvents.sort(Comparator.comparingDouble(SimulationEvent::getSimTime));

        // Step 7: Assign final sequential IDs after sorting
        for (int i = 0; i < allEvents.size(); i++) {
            allEvents.get(i).setEventId((long) (i + 1));
        }

        log.info("Simulation complete: {} total events generated with seed={}", allEvents.size(), config.getSeed());

        return SimulationResult.builder()
                .simulationId(simulationId)
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

            // Assigns a random type
            TaskType type = pickableTypes[rng.nextInt(pickableTypes.length)];

            // Assigns a random priority
            int priority = rng.nextInt(config.getSmallestPriority(), config.getLargestPriority());

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

                int maxDeps = Math.max(1, config.getMaxDependentTasks());
                int numDeps = Math.min(rng.nextInt(maxDeps) + 1, poolIndices.size()); // offset due to 1-indexing
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
        if (list == null || list.isEmpty()) throw new IllegalStateException("Cannot pick from empty or null list");
        return list.get(rng.nextInt(list.size()));
    }
}
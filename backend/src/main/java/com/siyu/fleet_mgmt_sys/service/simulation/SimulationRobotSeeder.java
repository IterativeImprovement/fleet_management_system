package com.siyu.fleet_mgmt_sys.service.simulation;

import com.siyu.fleet_mgmt_sys.dto.simulation.SimulationConfig;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.robot.LargeRobot;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.robot.StandardRobot;
import com.siyu.fleet_mgmt_sys.repository.LocationRepository;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationRobotSeeder {

    private final RobotRepository robotRepository;
    private final LocationRepository locationRepository;

    /**
     * Deterministically creates and persists simulation robots for a given simulation run.
     * Robot types are distributed randomly but deterministically based on the seed.
     * Returns the IDs of the persisted robots.
     */
    public List<Long> seed(SimulationConfig config, Long simulationId) {
        Random rng = new Random(config.getSeed() ^ 0xDDDD_DDDDL);

        // Allows for future extension of diff robot types
        RobotType[] pickableTypes = Arrays.stream(RobotType.values())
                .filter(t -> t != RobotType.UNINITIALISED)
                .toArray(RobotType[]::new);

        // Fetch location IDs upfront if not starting at base
        List<Long> locationIds = config.isStartAtBase()
                ? List.of()
                : locationRepository.findAllIds();

        if (!config.isStartAtBase() && locationIds.isEmpty()) {
            log.warn("startAtBase=false but no locations found in DB; using base coordinates");
        }

        List<Robot> robots = new ArrayList<>();

        for (int i = 0; i < config.getNumRobots(); i++) {
            RobotType type = pickableTypes[rng.nextInt(pickableTypes.length)];
            String name = String.format("SIM-%s-%03d", simulationId, i + 1);

            Robot robot = createRobot(type, name);
            robot.setSimulated(true);
            robot.setSimulationId(simulationId);

            // Set position
            if (config.isStartAtBase() || locationIds.isEmpty()) {
                robot.setPosition(config.getBaseLatitude(), config.getBaseLongitude());
            } else {
                Long locationId = locationIds.get(rng.nextInt(locationIds.size()));
                locationRepository.findById(locationId).ifPresent(loc ->
                        robot.setPosition(loc.getLatitude(), loc.getLongitude()));
            }

            robots.add(robot);
        }

        List<Robot> saved = robotRepository.saveAll(robots);
        log.info("Seeded {} simulation robots for simulationId={}", saved.size(), simulationId);

        return saved.stream().map(Robot::getId).toList();
    }

    private Robot createRobot(RobotType type, String name) {
        return switch (type) {
            case STANDARD -> new StandardRobot(name);
            case LARGE -> new LargeRobot(name);
            default -> throw new IllegalArgumentException("Unsupported robot type: " + type);
        };
    }

    public Map<Long, String> getRobotNames(List<Long> robotIds) {
        return robotRepository.findAllById(robotIds).stream()
                .collect(Collectors.toMap(Robot::getId, Robot::getName));
    }
}
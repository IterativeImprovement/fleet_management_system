package com.siyu.fleet_mgmt_sys.dto.simulation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Random;

@Getter
@Setter
@NoArgsConstructor
public class SimulationConfig {

    private Long seed;  // nullable: null means "pick a random seed" (see setRandomSeed)

    // Simulation duration
    private double durationSeconds = 259200.0;  // 3 days

    // Robots config
    private int numRobots = 10;
    private boolean startAtBase = true;

    // Base location (default: Bishan Depot)
    private double baseLatitude = 1.351858;
    private double baseLongitude = 103.848890;

    // Rates (events per second)
    private double taskArrivalRatePerSecond = 0.000193;
    private double malfunctionRatePerRobotPerSecond = 0.0000193;
    private double routeObstructionRatePerSecond = 0.0000386;

    // Task completion time range (seconds)
    private double minTaskCompletionSeconds = 10800.0;   // 3 hours
    private double maxTaskCompletionSeconds = 36000.0;   // 10 hours

    // Dependency config
    private double dependentTaskProbability = 0.3;
    private int maxDependentTasks = 1;
    private int dependencyPoolSize = 10;

    // Priority range
    private int largestPriority = 5;
    private int smallestPriority = 1;

    public void setRandomSeed() {
        this.seed = new Random().nextLong();
    }
}
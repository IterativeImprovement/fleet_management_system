package com.siyu.fleet_mgmt_sys.dto.simulation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Random;

@Getter
@Setter
@NoArgsConstructor
public class SimulationConfig {

    private long seed;

    // Simulation duration
    private double durationSeconds = 259200.0;  // 3 days

    // Rates (events per second)
    private double taskArrivalRatePerSecond = 0.000193;             // ~1 task per 86 min
    private double malfunctionRatePerRobotPerSecond = 0.0000193;    // ~1 per robot per 14h
    private double routeObstructionRatePerSecond = 0.0000386;       // ~1 per 7h

    // Task completion time range (seconds)
    private double minTaskCompletionSeconds = 10800.0;   // 3 hours
    private double maxTaskCompletionSeconds = 36000.0;   // 10 hours

    // Dependency config
    private double dependentTaskProbability = 0.3;   // 30% chance a task has dependencies
    private int maxDependentTasks = 3;
    private int dependencyPoolSize = 10;             // pick from 10 most recent tasks

    public void setRandomSeed() {
        this.seed = new Random().nextLong();
    }
}
package com.siyu.fleet_mgmt_sys.model.enums;

/**
 * The leg a robot is currently executing under backend-authoritative dispatch.
 * TO_TASK_START: repositioning from its current spot to a task's start.
 * EXECUTE_TASK:  running the task's own start→end route.
 * TO_BASE:       returning to base with nothing queued.
 * IDLE:          parked at base, no leg.
 */
public enum DispatchPhase {
    TO_TASK_START,
    EXECUTE_TASK,
    TO_BASE,
    IDLE,
    BEING_TOWED
}

package com.siyu.fleet_mgmt_sys.service.task.submission;

import com.siyu.fleet_mgmt_sys.exception.ClusterNotFoundException;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.task.Cluster;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.repository.TaskClusterRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*

The TaskClusterService clusters tasks by geographical proximity, saving on computational time.

Tasks can have the same or different clusters for their start and end points.

*/

@Service
@RequiredArgsConstructor
public class TaskClusterService { // this service clusters tasks that are close by so that allocation can become smarter

    private final TaskClusterRepository clusterRepository;

    @Transactional
    public void assignCluster(Task task) {
        assignPointToCluster(task, true);  // cluster the start point
        assignPointToCluster(task, false); // cluster the end point
    }

    private void assignPointToCluster(Task task, boolean isStart) { // assigns start and end points to potentially different clusters
        List<Cluster> clusters = clusterRepository.findAll();
        WayPoint wayPoint = isStart ? task.getStartWayPoint() : task.getEndWayPoint();
        if (wayPoint == null) {
            throw new IllegalArgumentException((isStart ? "Start" : "End") + " waypoint is required for clustering.");
        }

        double lat = wayPoint.getLatitude();
        double lng = wayPoint.getLongitude();

        Cluster match = clusters.stream()
                .filter(c -> isWithinThreshold(c, lat, lng))
                .min(Comparator.comparingDouble(c ->
                        haversineMetres(c.getCentroidLat(), c.getCentroidLng(), lat, lng)))
                .orElse(null);

        if (match != null) {
            attachTaskToCluster(task, match, isStart);
            updateCentroid(match);
            clusterRepository.save(match);
        } else {
            Cluster newCluster = new Cluster();
            newCluster.setCentroidLat(lat);
            newCluster.setCentroidLng(lng);
            attachTaskToCluster(task, newCluster, isStart);
            clusterRepository.save(newCluster);
            computeAdjacency(newCluster);
        }
    }

    private void attachTaskToCluster(Task task, Cluster cluster, boolean isStart) {
        if (isStart) {
            task.setStartCluster(cluster);
            cluster.getStartTasks().add(task);
        } else {
            task.setEndCluster(cluster);
            cluster.getEndTasks().add(task);
        }
    }

    private static final double MAX_RADIUS_METRES = 5000.0; // clusters are at most 5km in radius
    private static final int MAX_TASKS = 10;

    private boolean isWithinThreshold(Cluster cluster, double lat, double lng) {
        int count = cluster.getStartTasks().size() + cluster.getEndTasks().size();
        if (count >= MAX_TASKS) return false;
        double dist = haversineMetres(cluster.getCentroidLat(), cluster.getCentroidLng(), lat, lng);
        return dist < MAX_RADIUS_METRES;
    }

    private void updateCentroid(Cluster cluster) {
        updateCentroid(cluster, null);
    }

    private void updateCentroid(Cluster cluster, Long excludedTaskId) {
        // average start tasks' startpoints
        List<Task> startTasks = cluster.getStartTasks().stream()
                .filter(task -> excludedTaskId == null || !excludedTaskId.equals(task.getId()))
                .toList();
        // average end tasks' endpoints
        List<Task> endTasks = cluster.getEndTasks().stream()
                .filter(task -> excludedTaskId == null || !excludedTaskId.equals(task.getId()))
                .toList();

        List<double[]> points = new ArrayList<>();
        startTasks.forEach(t -> addPoint(points, t.getStartWayPoint()));
        endTasks.forEach(t -> addPoint(points, t.getEndWayPoint()));

        double avgLat = points.stream().mapToDouble(p -> p[0]).average().orElse(cluster.getCentroidLat());
        double avgLng = points.stream().mapToDouble(p -> p[1]).average().orElse(cluster.getCentroidLng());

        cluster.setCentroidLat(avgLat);
        cluster.setCentroidLng(avgLng);
    }

    private void addPoint(List<double[]> points, WayPoint wayPoint) {
        if (wayPoint != null) {
            points.add(new double[]{wayPoint.getLatitude(), wayPoint.getLongitude()});
        }
    }

    private double haversineMetres(double lat1, double lng1, double lat2, double lng2) { // calculates distance between two points on earth
        final int R = 6371000; // radius of earth in metres
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng/2) * Math.sin(dLng/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }

    private final double ADJACENCY_THRESHOLD_METRES = 1000.0; // clusters must be within 1km of each other to be considered adjacent

    private void computeAdjacency(Cluster newCluster) { // when a new cluster is added, compute its adjacency with other existing clusters
        List<Cluster> all = clusterRepository.findAll();
        List<Cluster> adjacent = all.stream()
                .filter(c -> !c.equals(newCluster))
                .filter(c -> haversineMetres(
                        newCluster.getCentroidLat(), newCluster.getCentroidLng(),
                        c.getCentroidLat(), c.getCentroidLng()
                ) < ADJACENCY_THRESHOLD_METRES)
                .collect(Collectors.toList());

        newCluster.setAdjacentClusters(adjacent);

        // also update existing clusters to include new one as neighbour
        adjacent.forEach(c -> {
            c.getAdjacentClusters().add(newCluster);
            clusterRepository.save(c);
        });

        clusterRepository.save(newCluster);
    }

    @Transactional
    public void refreshTopTasks(Long clusterId) { // updates the top task of each cluster
        Cluster cluster = clusterRepository.findById(clusterId).orElseThrow(
                () -> new ClusterNotFoundException(clusterId)
        );
        updateTopTasks(cluster, null);
        clusterRepository.save(cluster);
    }

    @Transactional
    public void removeTaskFromClusterCaches(Task task) {
        Map<Long, Cluster> affectedClusters = new LinkedHashMap<>();
        addCluster(affectedClusters, task.getStartCluster());
        addCluster(affectedClusters, task.getEndCluster());
        clusterRepository.findByTopStandardTaskOrTopLargeTask(task, task)
                .forEach(cluster -> addCluster(affectedClusters, cluster));

        affectedClusters.values().forEach(cluster -> {
            updateCentroid(cluster, task.getId());
            updateTopTasks(cluster, task.getId());
        });
        clusterRepository.saveAll(affectedClusters.values());
        clusterRepository.flush();
    }

    private void addCluster(Map<Long, Cluster> clusters, Cluster cluster) {
        if (cluster != null) {
            clusters.put(cluster.getId(), cluster);
        }
    }

    private void updateTopTasks(Cluster cluster, Long excludedTaskId) {
        List<Task> pending = cluster.getStartTasks().stream() // only tasks that start in this cluster will be considered
                .filter(task -> excludedTaskId == null || !excludedTaskId.equals(task.getId()))
                .filter(t -> t.getStatus() == TaskStatus.PENDING_ASSIGNMENT) // retrieves pending tasks
                .toList();

        Task topStandard = pending.stream()
                .max(Comparator.comparingDouble(task -> task.getPriorityFor(RobotType.STANDARD)))
                .orElse(null);

        Task topLarge = pending.stream()
                .max(Comparator.comparingDouble(task -> task.getPriorityFor(RobotType.LARGE)))
                .orElse(null);

        cluster.setTopStandardTask(topStandard);
        cluster.setTopLargeTask(topLarge);
    }
}

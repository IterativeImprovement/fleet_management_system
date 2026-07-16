package com.siyu.fleet_mgmt_sys.repository;

import com.siyu.fleet_mgmt_sys.model.graph.GraphEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface GraphEdgeRepository extends JpaRepository<GraphEdge, Long> {

    List<GraphEdge> findByFromNodeId(Long fromNodeId);

    @Modifying
    @Transactional
    @Query("UPDATE GraphEdge e SET e.currentSpeedBand = :band WHERE e.linkId = :linkId")
    void updateSpeedBand(@Param("linkId") String linkId, @Param("band") int band);

    @Query("SELECT e FROM GraphEdge e JOIN FETCH e.fromNode JOIN FETCH e.toNode")
    List<GraphEdge> findAllWithNodes();
}
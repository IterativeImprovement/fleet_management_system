package com.siyu.fleet_mgmt_sys.repository;

import com.siyu.fleet_mgmt_sys.model.Location;
import com.siyu.fleet_mgmt_sys.model.enums.LocationSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findTop10ByNameContainingIgnoreCaseOrAddressContainingIgnoreCaseOrPostalCodeContainingIgnoreCase(
            String name,
            String address,
            String postalCode
    );

    Optional<Location> findBySourceAndExternalId(LocationSource source, String externalId);

    @Query("SELECT l.id FROM Location l")
    List<Long> findAllIds();

    @Query("SELECT l.id FROM Location l WHERE l.source = :source")
    List<Long> findAllIdsBySource(@Param("source") LocationSource source);

    List<Location> findAllBySource(LocationSource source);

    long countBySource(LocationSource source);
}

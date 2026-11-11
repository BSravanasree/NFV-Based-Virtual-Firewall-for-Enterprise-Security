package com.nexus.nfv.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long>, JpaSpecificationExecutor<SecurityAlert> {

    Optional<SecurityAlert> findFirstByDetectorAndSourceIpAndStatusInOrderByLastSeenDesc(
            String detector, String sourceIp, Collection<SecurityAlert.Status> statuses);

    long countByStatusIn(Collection<SecurityAlert.Status> statuses);

    List<SecurityAlert> findTop10ByOrderByLastSeenDesc();

    @Query("select a.severity as severity, count(a) as total from SecurityAlert a where a.status in :statuses group by a.severity")
    List<SeverityCount> countOpenBySeverity(@Param("statuses") Collection<SecurityAlert.Status> statuses);

    List<SecurityAlert> findByLastSeenAfterOrderByLastSeenAsc(Instant after);

    interface SeverityCount {
        SecurityAlert.Severity getSeverity();

        long getTotal();
    }
}

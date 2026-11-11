package com.nexus.nfv.vnf;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface VnfMetricSampleRepository extends JpaRepository<VnfMetricSample, Long> {

    List<VnfMetricSample> findByVnfIdAndSampledAtAfterOrderBySampledAtAsc(Long vnfId, Instant after);

    List<VnfMetricSample> findBySampledAtAfterOrderBySampledAtAsc(Instant after);

    @Modifying
    @Query("delete from VnfMetricSample s where s.sampledAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

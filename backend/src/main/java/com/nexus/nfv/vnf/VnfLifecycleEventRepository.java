package com.nexus.nfv.vnf;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VnfLifecycleEventRepository extends JpaRepository<VnfLifecycleEvent, Long> {

    List<VnfLifecycleEvent> findByVnfIdOrderByOccurredAtDesc(Long vnfId);

    List<VnfLifecycleEvent> findTop15ByOrderByOccurredAtDesc();
}

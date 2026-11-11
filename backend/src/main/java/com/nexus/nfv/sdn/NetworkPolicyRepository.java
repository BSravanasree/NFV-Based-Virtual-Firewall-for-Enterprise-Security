package com.nexus.nfv.sdn;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkPolicyRepository extends JpaRepository<NetworkPolicy, Long> {

    boolean existsByName(String name);

    java.util.List<NetworkPolicy> findAllByOrderByPriorityDesc();
}

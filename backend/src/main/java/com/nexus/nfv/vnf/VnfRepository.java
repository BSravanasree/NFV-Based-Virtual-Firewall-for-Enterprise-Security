package com.nexus.nfv.vnf;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface VnfRepository extends JpaRepository<Vnf, Long> {

    boolean existsByName(String name);

    List<Vnf> findByStateIn(Collection<VnfState> states);

    List<Vnf> findAllByOrderByCreatedAtDesc();

    long countByState(VnfState state);
}

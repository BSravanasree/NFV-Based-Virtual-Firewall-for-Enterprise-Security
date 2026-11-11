package com.nexus.nfv.integration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ControllerConnectionRepository extends JpaRepository<ControllerConnection, Long> {

    java.util.Optional<ControllerConnection> findByControllerTypeAndName(String type, String name);

    java.util.List<ControllerConnection> findAllByOrderByControllerTypeAscNameAsc();
}

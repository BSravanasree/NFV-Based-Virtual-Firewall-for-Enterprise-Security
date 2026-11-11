package com.nexus.nfv.sdn;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkHostRepository extends JpaRepository<NetworkHost, Long> {

    java.util.Optional<NetworkHost> findByHostIdAndDataSource(String hostId, com.nexus.nfv.common.DataSource ds);
}

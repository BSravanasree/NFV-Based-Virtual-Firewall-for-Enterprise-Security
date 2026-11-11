package com.nexus.nfv.sdn;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkDeviceRepository extends JpaRepository<NetworkDevice, Long> {

    java.util.Optional<NetworkDevice> findByDeviceIdAndDataSource(String deviceId, com.nexus.nfv.common.DataSource ds);

    java.util.List<NetworkDevice> findByDataSource(com.nexus.nfv.common.DataSource ds);
}

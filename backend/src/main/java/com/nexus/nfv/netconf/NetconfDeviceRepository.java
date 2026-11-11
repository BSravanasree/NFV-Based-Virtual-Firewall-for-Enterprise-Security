package com.nexus.nfv.netconf;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NetconfDeviceRepository extends JpaRepository<NetconfDevice, Long> {

    boolean existsByName(String name);

    java.util.Optional<NetconfDevice> findFirstByVnfId(Long vnfId);
}

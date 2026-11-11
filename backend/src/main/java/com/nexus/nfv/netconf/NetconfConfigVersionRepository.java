package com.nexus.nfv.netconf;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NetconfConfigVersionRepository extends JpaRepository<NetconfConfigVersion, Long> {

    java.util.List<NetconfConfigVersion> findByDeviceIdOrderByVersionNoDesc(Long deviceId);

    java.util.Optional<NetconfConfigVersion> findFirstByDeviceIdOrderByVersionNoDesc(Long deviceId);

    java.util.Optional<NetconfConfigVersion> findFirstByDeviceIdAndStatusOrderByVersionNoDesc(Long deviceId, String status);
}

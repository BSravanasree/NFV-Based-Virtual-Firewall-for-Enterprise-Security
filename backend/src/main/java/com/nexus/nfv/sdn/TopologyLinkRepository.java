package com.nexus.nfv.sdn;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopologyLinkRepository extends JpaRepository<TopologyLink, Long> {

    java.util.Optional<TopologyLink> findBySrcDeviceAndSrcPortAndDstDeviceAndDstPortAndDataSource(String sd, String sp, String dd, String dp, com.nexus.nfv.common.DataSource ds);
}

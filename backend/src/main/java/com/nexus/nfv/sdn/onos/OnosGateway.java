package com.nexus.nfv.sdn.onos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.nfv.common.DataSource;

/**
 * Port to the SDN controller. Payloads are ONOS REST JSON (onos/v1) in both implementations so the
 * service layer is identical in REAL and DEMO mode; only {@link #dataSource()} differs.
 */
public interface OnosGateway {

    DataSource dataSource();

    String endpoint();

    /** GET /cluster/local or equivalent – controller identity/version. */
    JsonNode controllerInfo();

    JsonNode devices();

    JsonNode ports(String deviceId);

    JsonNode portStatistics(String deviceId);

    JsonNode hosts();

    JsonNode links();

    JsonNode flows(String deviceIdOrNull);

    JsonNode applications();

    /** Installs a flow and returns the controller-assigned flow id. */
    String installFlow(String deviceId, ObjectNode flow, String appId);

    void removeFlow(String deviceId, String flowId);
}

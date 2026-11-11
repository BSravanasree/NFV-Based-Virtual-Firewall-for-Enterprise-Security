-- =====================================================================
-- NEXUS NFV - baseline schema (MySQL 8.x, InnoDB, utf8mb4)
-- Portable subset of SQL so the same migration also runs on H2 (MySQL
-- mode) for automated tests.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Identity & access
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(32)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
);

CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    username              VARCHAR(64)  NOT NULL,
    full_name             VARCHAR(128) NOT NULL,
    email                 VARCHAR(160) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    role_id               BIGINT       NOT NULL,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          DATETIME(3)  NULL,
    last_login_at         DATETIME(3)  NULL,
    password_changed_at   DATETIME(3)  NULL,
    created_at            DATETIME(3)  NOT NULL,
    updated_at            DATETIME(3)  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE user_sessions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    jti         VARCHAR(64)  NOT NULL,
    user_id     BIGINT       NOT NULL,
    issued_at   DATETIME(3)  NOT NULL,
    expires_at  DATETIME(3)  NOT NULL,
    revoked_at  DATETIME(3)  NULL,
    ip_address  VARCHAR(64)  NULL,
    user_agent  VARCHAR(255) NULL,
    CONSTRAINT pk_user_sessions PRIMARY KEY (id),
    CONSTRAINT uk_user_sessions_jti UNIQUE (jti),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_user_sessions_user ON user_sessions (user_id, expires_at);

CREATE TABLE audit_logs (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(3)   NOT NULL,
    user_id     BIGINT        NULL,
    username    VARCHAR(64)   NOT NULL,
    action      VARCHAR(64)   NOT NULL,
    category    VARCHAR(32)   NOT NULL,
    target_type VARCHAR(64)   NULL,
    target_id   VARCHAR(128)  NULL,
    outcome     VARCHAR(16)   NOT NULL,
    details     VARCHAR(2000) NULL,
    ip_address  VARCHAR(64)   NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_audit_occurred ON audit_logs (occurred_at);
CREATE INDEX idx_audit_username ON audit_logs (username, occurred_at);
CREATE INDEX idx_audit_action ON audit_logs (action, occurred_at);

-- ---------------------------------------------------------------------
-- Virtual Network Functions
-- ---------------------------------------------------------------------
CREATE TABLE vnfs (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(63)   NOT NULL,
    vnf_type              VARCHAR(32)   NOT NULL,
    description           VARCHAR(500)  NULL,
    orchestrator          VARCHAR(16)   NOT NULL,
    state                 VARCHAR(24)   NOT NULL,
    health                VARCHAR(16)   NOT NULL,
    external_id           VARCHAR(128)  NULL,
    mgmt_ip               VARCHAR(45)   NULL,
    lan_ip                VARCHAR(45)   NULL,
    wan_ip                VARCHAR(45)   NULL,
    agent_url             VARCHAR(255)  NULL,
    cpu_limit             DECIMAL(5,2)  NOT NULL,
    memory_limit_mb       INT           NOT NULL,
    default_action        VARCHAR(8)    NOT NULL,
    rules_revision        BIGINT        NOT NULL DEFAULT 0,
    applied_revision      BIGINT        NOT NULL DEFAULT 0,
    rules_applied_at      DATETIME(3)   NULL,
    sync_status           VARCHAR(16)   NOT NULL,
    cpu_percent           DECIMAL(6,2)  NULL,
    memory_percent        DECIMAL(6,2)  NULL,
    memory_usage_mb       DECIMAL(10,2) NULL,
    rx_bytes              BIGINT        NULL,
    tx_bytes              BIGINT        NULL,
    throughput_bps        BIGINT        NULL,
    metrics_source        VARCHAR(16)   NULL,
    metrics_updated_at    DATETIME(3)   NULL,
    last_error            VARCHAR(1000) NULL,
    created_by            VARCHAR(64)   NOT NULL,
    created_at            DATETIME(3)   NOT NULL,
    updated_at            DATETIME(3)   NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_vnfs PRIMARY KEY (id),
    CONSTRAINT uk_vnfs_name UNIQUE (name)
);
CREATE INDEX idx_vnfs_state ON vnfs (state);

CREATE TABLE vnf_lifecycle_events (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    vnf_id      BIGINT        NOT NULL,
    operation   VARCHAR(24)   NOT NULL,
    from_state  VARCHAR(24)   NULL,
    to_state    VARCHAR(24)   NOT NULL,
    outcome     VARCHAR(16)   NOT NULL,
    message     VARCHAR(1000) NULL,
    actor       VARCHAR(64)   NOT NULL,
    occurred_at DATETIME(3)   NOT NULL,
    CONSTRAINT pk_vnf_lifecycle_events PRIMARY KEY (id),
    CONSTRAINT fk_vnf_events_vnf FOREIGN KEY (vnf_id) REFERENCES vnfs (id) ON DELETE CASCADE
);
CREATE INDEX idx_vnf_events_vnf ON vnf_lifecycle_events (vnf_id, occurred_at);

CREATE TABLE vnf_metric_samples (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    vnf_id          BIGINT        NOT NULL,
    sampled_at      DATETIME(3)   NOT NULL,
    cpu_percent     DECIMAL(6,2)  NULL,
    memory_percent  DECIMAL(6,2)  NULL,
    memory_usage_mb DECIMAL(10,2) NULL,
    rx_bytes        BIGINT        NULL,
    tx_bytes        BIGINT        NULL,
    throughput_bps  BIGINT        NULL,
    data_source     VARCHAR(16)   NOT NULL,
    CONSTRAINT pk_vnf_metric_samples PRIMARY KEY (id),
    CONSTRAINT fk_vnf_metrics_vnf FOREIGN KEY (vnf_id) REFERENCES vnfs (id) ON DELETE CASCADE
);
CREATE INDEX idx_vnf_metrics_vnf ON vnf_metric_samples (vnf_id, sampled_at);

-- ---------------------------------------------------------------------
-- Firewall
-- ---------------------------------------------------------------------
CREATE TABLE firewall_rules (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    vnf_id              BIGINT       NOT NULL,
    name                VARCHAR(64)  NOT NULL,
    priority            INT          NOT NULL,
    action              VARCHAR(8)   NOT NULL,
    protocol            VARCHAR(8)   NOT NULL,
    source_cidr         VARCHAR(43)  NULL,
    destination_cidr    VARCHAR(43)  NULL,
    source_port         VARCHAR(11)  NULL,
    destination_port    VARCHAR(11)  NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    log_enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    description         VARCHAR(255) NULL,
    hit_packets         BIGINT       NOT NULL DEFAULT 0,
    hit_bytes           BIGINT       NOT NULL DEFAULT 0,
    counters_updated_at DATETIME(3)  NULL,
    created_by          VARCHAR(64)  NOT NULL,
    created_at          DATETIME(3)  NOT NULL,
    updated_at          DATETIME(3)  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_firewall_rules PRIMARY KEY (id),
    CONSTRAINT uk_fw_rules_priority UNIQUE (vnf_id, priority),
    CONSTRAINT uk_fw_rules_name UNIQUE (vnf_id, name),
    CONSTRAINT fk_fw_rules_vnf FOREIGN KEY (vnf_id) REFERENCES vnfs (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- Traffic telemetry
-- ---------------------------------------------------------------------
CREATE TABLE traffic_events (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    vnf_id        BIGINT      NOT NULL,
    occurred_at   DATETIME(3) NOT NULL,
    action        VARCHAR(8)  NOT NULL,
    protocol      VARCHAR(8)  NOT NULL,
    src_ip        VARCHAR(45) NOT NULL,
    dst_ip        VARCHAR(45) NOT NULL,
    src_port      INT         NULL,
    dst_port      INT         NULL,
    rule_id       BIGINT      NULL,
    direction     VARCHAR(8)  NULL,
    packet_length INT         NULL,
    data_source   VARCHAR(16) NOT NULL,
    agent_seq     BIGINT      NULL,
    CONSTRAINT pk_traffic_events PRIMARY KEY (id),
    CONSTRAINT fk_traffic_events_vnf FOREIGN KEY (vnf_id) REFERENCES vnfs (id) ON DELETE CASCADE
);
CREATE INDEX idx_traffic_events_time ON traffic_events (occurred_at);
CREATE INDEX idx_traffic_events_src ON traffic_events (src_ip, occurred_at);
CREATE INDEX idx_traffic_events_vnf ON traffic_events (vnf_id, occurred_at);

CREATE TABLE traffic_samples (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    vnf_id          BIGINT      NOT NULL,
    sampled_at      DATETIME(3) NOT NULL,
    interval_ms     BIGINT      NOT NULL,
    allowed_packets BIGINT      NOT NULL,
    allowed_bytes   BIGINT      NOT NULL,
    blocked_packets BIGINT      NOT NULL,
    blocked_bytes   BIGINT      NOT NULL,
    tcp_packets     BIGINT      NOT NULL,
    udp_packets     BIGINT      NOT NULL,
    icmp_packets    BIGINT      NOT NULL,
    inbound_bytes   BIGINT      NOT NULL,
    outbound_bytes  BIGINT      NOT NULL,
    data_source     VARCHAR(16) NOT NULL,
    CONSTRAINT pk_traffic_samples PRIMARY KEY (id),
    CONSTRAINT fk_traffic_samples_vnf FOREIGN KEY (vnf_id) REFERENCES vnfs (id) ON DELETE CASCADE
);
CREATE INDEX idx_traffic_samples_time ON traffic_samples (sampled_at);
CREATE INDEX idx_traffic_samples_vnf ON traffic_samples (vnf_id, sampled_at);

-- ---------------------------------------------------------------------
-- Threat detection
-- ---------------------------------------------------------------------
CREATE TABLE security_alerts (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    vnf_id          BIGINT        NULL,
    detector        VARCHAR(48)   NOT NULL,
    event_type      VARCHAR(64)   NOT NULL,
    severity        VARCHAR(16)   NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    source_ip       VARCHAR(45)   NULL,
    destination_ip  VARCHAR(45)   NULL,
    description     VARCHAR(1000) NOT NULL,
    evidence_count  INT           NOT NULL,
    first_seen      DATETIME(3)   NOT NULL,
    last_seen       DATETIME(3)   NOT NULL,
    assigned_to     VARCHAR(64)   NULL,
    resolution_note VARCHAR(1000) NULL,
    created_at      DATETIME(3)   NOT NULL,
    updated_at      DATETIME(3)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_security_alerts PRIMARY KEY (id),
    CONSTRAINT fk_alerts_vnf FOREIGN KEY (vnf_id) REFERENCES vnfs (id) ON DELETE SET NULL
);
CREATE INDEX idx_alerts_status ON security_alerts (status, severity);
CREATE INDEX idx_alerts_dedup ON security_alerts (detector, source_ip, status);
CREATE INDEX idx_alerts_last_seen ON security_alerts (last_seen);

-- ---------------------------------------------------------------------
-- SDN inventory (ONOS / OpenFlow)
-- ---------------------------------------------------------------------
CREATE TABLE network_devices (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    device_id    VARCHAR(128) NOT NULL,
    device_type  VARCHAR(32)  NULL,
    manufacturer VARCHAR(128) NULL,
    hw_version   VARCHAR(128) NULL,
    sw_version   VARCHAR(128) NULL,
    serial       VARCHAR(128) NULL,
    protocol     VARCHAR(32)  NULL,
    management_address VARCHAR(64) NULL,
    available    BOOLEAN      NOT NULL,
    data_source  VARCHAR(16)  NOT NULL,
    last_seen    DATETIME(3)  NOT NULL,
    CONSTRAINT pk_network_devices PRIMARY KEY (id),
    CONSTRAINT uk_network_devices UNIQUE (device_id, data_source)
);

CREATE TABLE network_hosts (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    host_id         VARCHAR(128) NOT NULL,
    mac             VARCHAR(32)  NOT NULL,
    vlan            VARCHAR(16)  NULL,
    ip_addresses    VARCHAR(255) NULL,
    location_device VARCHAR(128) NULL,
    location_port   VARCHAR(32)  NULL,
    data_source     VARCHAR(16)  NOT NULL,
    last_seen       DATETIME(3)  NOT NULL,
    CONSTRAINT pk_network_hosts PRIMARY KEY (id),
    CONSTRAINT uk_network_hosts UNIQUE (host_id, data_source)
);

CREATE TABLE topology_links (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    src_device  VARCHAR(128) NOT NULL,
    src_port    VARCHAR(32)  NOT NULL,
    dst_device  VARCHAR(128) NOT NULL,
    dst_port    VARCHAR(32)  NOT NULL,
    link_type   VARCHAR(32)  NULL,
    state       VARCHAR(16)  NULL,
    data_source VARCHAR(16)  NOT NULL,
    last_seen   DATETIME(3)  NOT NULL,
    CONSTRAINT pk_topology_links PRIMARY KEY (id),
    CONSTRAINT uk_topology_links UNIQUE (src_device, src_port, dst_device, dst_port, data_source)
);

CREATE TABLE network_policies (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    name             VARCHAR(64)   NOT NULL,
    description      VARCHAR(255)  NULL,
    device_id        VARCHAR(128)  NOT NULL,
    priority         INT           NOT NULL,
    ip_protocol      VARCHAR(8)    NOT NULL,
    source_cidr      VARCHAR(43)   NULL,
    destination_cidr VARCHAR(43)   NULL,
    destination_port INT           NULL,
    action           VARCHAR(24)   NOT NULL,
    output_port      VARCHAR(32)   NULL,
    onos_flow_id     VARCHAR(64)   NULL,
    status           VARCHAR(16)   NOT NULL,
    data_source      VARCHAR(16)   NULL,
    last_error       VARCHAR(1000) NULL,
    created_by       VARCHAR(64)   NOT NULL,
    created_at       DATETIME(3)   NOT NULL,
    updated_at       DATETIME(3)   NOT NULL,
    CONSTRAINT pk_network_policies PRIMARY KEY (id),
    CONSTRAINT uk_network_policies_name UNIQUE (name)
);

-- ---------------------------------------------------------------------
-- Integrations
-- ---------------------------------------------------------------------
CREATE TABLE controller_connections (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    controller_type VARCHAR(16)   NOT NULL,
    name            VARCHAR(64)   NOT NULL,
    endpoint        VARCHAR(255)  NULL,
    mode            VARCHAR(16)   NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    latency_ms      BIGINT        NULL,
    details         VARCHAR(2000) NULL,
    last_error      VARCHAR(1000) NULL,
    last_checked_at DATETIME(3)   NULL,
    CONSTRAINT pk_controller_connections PRIMARY KEY (id),
    CONSTRAINT uk_controller_connections UNIQUE (controller_type, name)
);

CREATE TABLE netconf_devices (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    name              VARCHAR(64)   NOT NULL,
    host              VARCHAR(255)  NOT NULL,
    port              INT           NOT NULL,
    username          VARCHAR(64)   NOT NULL,
    password_enc      VARCHAR(512)  NOT NULL,
    vnf_id            BIGINT        NULL,
    status            VARCHAR(16)   NOT NULL,
    capabilities      TEXT          NULL,
    last_error        VARCHAR(1000) NULL,
    last_connected_at DATETIME(3)   NULL,
    created_by        VARCHAR(64)   NOT NULL,
    created_at        DATETIME(3)   NOT NULL,
    updated_at        DATETIME(3)   NOT NULL,
    CONSTRAINT pk_netconf_devices PRIMARY KEY (id),
    CONSTRAINT uk_netconf_devices_name UNIQUE (name),
    CONSTRAINT fk_netconf_devices_vnf FOREIGN KEY (vnf_id) REFERENCES vnfs (id) ON DELETE SET NULL
);

CREATE TABLE netconf_config_versions (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    device_id   BIGINT        NOT NULL,
    version_no  INT           NOT NULL,
    operation   VARCHAR(16)   NOT NULL,
    datastore   VARCHAR(16)   NOT NULL,
    config_xml  MEDIUMTEXT    NOT NULL,
    checksum    VARCHAR(64)   NOT NULL,
    status      VARCHAR(16)   NOT NULL,
    message     VARCHAR(1000) NULL,
    created_by  VARCHAR(64)   NOT NULL,
    created_at  DATETIME(3)   NOT NULL,
    CONSTRAINT pk_netconf_config_versions PRIMARY KEY (id),
    CONSTRAINT uk_netconf_versions UNIQUE (device_id, version_no),
    CONSTRAINT fk_netconf_versions_device FOREIGN KEY (device_id) REFERENCES netconf_devices (id) ON DELETE CASCADE
);

CREATE TABLE system_settings (
    setting_key   VARCHAR(64)  NOT NULL,
    setting_value VARCHAR(255) NOT NULL,
    value_type    VARCHAR(16)  NOT NULL,
    category      VARCHAR(32)  NOT NULL,
    description   VARCHAR(255) NOT NULL,
    updated_by    VARCHAR(64)  NULL,
    updated_at    DATETIME(3)  NULL,
    CONSTRAINT pk_system_settings PRIMARY KEY (setting_key)
);

-- ---------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------
INSERT INTO roles (name, description) VALUES
 ('ADMIN', 'Full platform administration, user management and all infrastructure operations'),
 ('NETWORK_OPERATOR', 'Deploys and operates VNFs, firewall rules, SDN policies and NETCONF configuration'),
 ('SECURITY_ANALYST', 'Read access to infrastructure; investigates traffic and manages security alerts');

INSERT INTO system_settings (setting_key, setting_value, value_type, category, description) VALUES
 ('detection.window.seconds', '300', 'INT', 'DETECTION', 'Sliding window used by all threat detectors'),
 ('detection.portscan.distinct_ports', '10', 'INT', 'DETECTION', 'Distinct destination ports from one source inside the window that indicate a port scan'),
 ('detection.blocked.threshold', '20', 'INT', 'DETECTION', 'Blocked packets from one source inside the window that raise a repeated-blocked alert'),
 ('detection.frequency.threshold', '200', 'INT', 'DETECTION', 'Logged connection attempts from one source inside the window that indicate unusual frequency'),
 ('detection.bruteforce.threshold', '5', 'INT', 'DETECTION', 'Blocked attempts to a sensitive service port (22, 23, 3389, 445, 3306) that raise an unauthorized-access alert'),
 ('detection.enabled', 'true', 'BOOL', 'DETECTION', 'Enable or disable the rule-based detection engine'),
 ('retention.telemetry.days', '7', 'INT', 'RETENTION', 'Days to retain traffic events and metric samples'),
 ('retention.audit.days', '365', 'INT', 'RETENTION', 'Days to retain audit log records');

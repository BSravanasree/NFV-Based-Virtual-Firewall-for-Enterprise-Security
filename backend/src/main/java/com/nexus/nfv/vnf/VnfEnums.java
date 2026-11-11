package com.nexus.nfv.vnf;

/** Small enums of the VNF domain grouped in one file for readability. */
public final class VnfEnums {

    private VnfEnums() {
    }

    public enum Health { HEALTHY, DEGRADED, UNREACHABLE, UNKNOWN }

    public enum OrchestratorType { DOCKER, OSM }

    public enum VnfType { FIREWALL }

    public enum Operation { CREATE, DEPLOY, START, STOP, RESTART, CONFIGURE, SCALE, TERMINATE, HEAL }

    public enum SyncStatus { IN_SYNC, PENDING, FAILED, DRIFT, UNKNOWN }

    public enum DefaultAction { DROP, ACCEPT }
}

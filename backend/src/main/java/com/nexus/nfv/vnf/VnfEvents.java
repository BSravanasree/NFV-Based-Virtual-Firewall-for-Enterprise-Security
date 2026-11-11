package com.nexus.nfv.vnf;

/** Domain events published (after commit) by the VNF lifecycle; consumed by firewall sync and NETCONF. */
public final class VnfEvents {

    private VnfEvents() {
    }

    /** VNF became ACTIVE (deployed, started or restarted); desired firewall policy must be (re)applied. */
    public record Activated(Long vnfId, boolean freshDeployment, String netconfHost, Integer netconfPort,
                            String netconfPassword) {
    }

    /** VNF was terminated; dependent records should be detached. */
    public record Terminated(Long vnfId) {
    }
}

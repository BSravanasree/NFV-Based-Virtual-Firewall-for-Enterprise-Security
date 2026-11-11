package com.nexus.nfv.common;

/**
 * Provenance of every metric and inventory record shown in the UI.
 * <ul>
 *   <li>LIVE – measured from production infrastructure (OSM-managed VNF, real ONOS controller).</li>
 *   <li>LAB – measured from the local NFV lab (real containers, real nftables counters).</li>
 *   <li>DEMO – fixture data used only when an integration is explicitly put in demo mode.</li>
 * </ul>
 */
public enum DataSource {
    LIVE,
    LAB,
    DEMO
}

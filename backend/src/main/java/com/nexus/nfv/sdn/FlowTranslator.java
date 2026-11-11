package com.nexus.nfv.sdn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.nfv.common.exception.ApiException;

/**
 * Compiles a {@link NetworkPolicy} into an ONOS flow-rule JSON (OpenFlow 1.3 match + instructions).
 *
 * <pre>
 * match:   ETH_TYPE=0x0800 [IP_PROTO] [IPV4_SRC] [IPV4_DST] [TCP_DST|UDP_DST]
 * action:  DROP              -> NOACTION (no output = packet dropped)
 *          ALLOW             -> OUTPUT:NORMAL (OVS L2 pipeline)
 *          FORWARD           -> OUTPUT:&lt;port&gt;
 *          STEER_TO_FIREWALL -> OUTPUT:&lt;port of the firewall VNF&gt; (service chaining)
 * </pre>
 */
public final class FlowTranslator {

    private FlowTranslator() {
    }

    public static ObjectNode toOnosFlow(NetworkPolicy p, ObjectMapper mapper) {
        ObjectNode flow = mapper.createObjectNode();
        flow.put("priority", p.getPriority());
        flow.put("timeout", 0);
        flow.put("isPermanent", true);
        flow.put("deviceId", p.getDeviceId());

        ArrayNode criteria = mapper.createArrayNode();
        criteria.addObject().put("type", "ETH_TYPE").put("ethType", "0x0800");
        String proto = p.getIpProtocol() == null ? "ANY" : p.getIpProtocol().toUpperCase();
        switch (proto) {
            case "TCP" -> criteria.addObject().put("type", "IP_PROTO").put("protocol", 6);
            case "UDP" -> criteria.addObject().put("type", "IP_PROTO").put("protocol", 17);
            case "ICMP" -> criteria.addObject().put("type", "IP_PROTO").put("protocol", 1);
            case "ANY" -> {
            }
            default -> throw ApiException.badRequest("Unsupported protocol " + proto);
        }
        if (p.getSourceCidr() != null) criteria.addObject().put("type", "IPV4_SRC").put("ip", p.getSourceCidr());
        if (p.getDestinationCidr() != null) criteria.addObject().put("type", "IPV4_DST").put("ip", p.getDestinationCidr());
        if (p.getDestinationPort() != null) {
            if ("TCP".equals(proto)) criteria.addObject().put("type", "TCP_DST").put("tcpPort", p.getDestinationPort());
            else if ("UDP".equals(proto)) criteria.addObject().put("type", "UDP_DST").put("udpPort", p.getDestinationPort());
            else throw ApiException.badRequest("A destination port requires protocol TCP or UDP");
        }
        flow.putObject("selector").set("criteria", criteria);

        ArrayNode instructions = mapper.createArrayNode();
        switch (p.getAction()) {
            case DROP -> instructions.addObject().put("type", "NOACTION");
            case ALLOW -> instructions.addObject().put("type", "OUTPUT").put("port", "NORMAL");
            case FORWARD, STEER_TO_FIREWALL -> {
                if (p.getOutputPort() == null || !p.getOutputPort().matches("^\\d{1,5}$")) {
                    throw ApiException.badRequest(p.getAction() + " requires a numeric output port");
                }
                instructions.addObject().put("type", "OUTPUT").put("port", p.getOutputPort());
            }
        }
        flow.putObject("treatment").set("instructions", instructions);
        return flow;
    }
}

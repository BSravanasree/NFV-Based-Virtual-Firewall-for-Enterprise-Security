package com.nexus.nfv.netconf;

import com.nexus.nfv.firewall.FirewallRule;
import com.nexus.nfv.netconf.client.Xml;
import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfEnums;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Encodes/decodes firewall policy to/from the {@code nexus-firewall} YANG model
 * (vnf-firewall/netconf/yang/nexus-firewall@2026-11-01.yang, namespace urn:nexus:nfv:firewall).
 */
public final class FirewallYangCodec {

    public static final String NS = "urn:nexus:nfv:firewall";
    public static final String FILTER = "<firewall xmlns=\"" + NS + "\"/>";

    private FirewallYangCodec() {
    }

    /** Builds the &lt;firewall&gt; subtree with nc:operation="replace" so the whole policy is swapped atomically. */
    public static String encode(Vnf vnf, List<FirewallRule> rules) {
        StringBuilder sb = new StringBuilder();
        sb.append("<firewall xmlns=\"").append(NS).append("\" xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" nc:operation=\"replace\">");
        sb.append("<default-action>").append(vnf.getDefaultAction() == VnfEnums.DefaultAction.ACCEPT ? "accept" : "drop")
                .append("</default-action>");
        sb.append("<revision>").append(vnf.getRulesRevision()).append("</revision>");
        for (FirewallRule r : rules) {
            sb.append("<rule>");
            leaf(sb, "id", String.valueOf(r.getId()));
            leaf(sb, "name", r.getName());
            leaf(sb, "priority", String.valueOf(r.getPriority()));
            leaf(sb, "action", r.getAction() == FirewallRule.Action.ALLOW ? "accept" : "drop");
            leaf(sb, "protocol", r.getProtocol().name().toLowerCase());
            if (r.getSourceCidr() != null) leaf(sb, "source-prefix", r.getSourceCidr());
            if (r.getDestinationCidr() != null) leaf(sb, "destination-prefix", r.getDestinationCidr());
            if (r.getSourcePort() != null) leaf(sb, "source-port", r.getSourcePort());
            if (r.getDestinationPort() != null) leaf(sb, "destination-port", r.getDestinationPort());
            leaf(sb, "enabled", String.valueOf(r.isEnabled()));
            leaf(sb, "log", String.valueOf(r.isLogEnabled()));
            sb.append("</rule>");
        }
        sb.append("</firewall>");
        return sb.toString();
    }

    private static void leaf(StringBuilder sb, String name, String value) {
        sb.append('<').append(name).append('>').append(Xml.escape(value)).append("</").append(name).append('>');
    }

    /**
     * Canonical, order-independent representation of a firewall subtree (ignores the revision leaf and
     * formatting) used for drift detection between datastore content and recorded versions.
     */
    public static String canonical(String firewallXml) {
        if (firewallXml == null || firewallXml.isBlank()) return "";
        Document d = Xml.parse("<root>" + firewallXml + "</root>");
        NodeList fws = d.getElementsByTagNameNS(NS, "firewall");
        if (fws.getLength() == 0) return "";
        Element fw = (Element) fws.item(0);
        List<String> rules = new ArrayList<>();
        NodeList rl = fw.getElementsByTagNameNS(NS, "rule");
        for (int i = 0; i < rl.getLength(); i++) {
            Element r = (Element) rl.item(i);
            rules.add(String.join("|", t(r, "id"), t(r, "priority"), t(r, "action"), def(t(r, "protocol"), "any"),
                    t(r, "source-prefix"), t(r, "destination-prefix"), t(r, "source-port"), t(r, "destination-port"),
                    def(t(r, "enabled"), "true"), def(t(r, "log"), "true")));
        }
        rules.sort(String::compareTo);
        return "default=" + def(direct(fw, "default-action"), "drop") + ";" + String.join(";", rules);
    }

    public static String checksum(String xml) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(canonical(xml).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static int ruleCount(String xml) {
        String c = canonical(xml);
        int idx = c.indexOf(';');
        return idx < 0 || idx == c.length() - 1 ? 0 : c.substring(idx + 1).split(";").length;
    }

    private static String direct(Element parent, String name) {
        NodeList l = parent.getChildNodes();
        for (int i = 0; i < l.getLength(); i++) {
            if (l.item(i) instanceof Element e && name.equals(e.getLocalName())) return e.getTextContent().trim();
        }
        return "";
    }

    private static String t(Element e, String name) {
        return direct(e, name);
    }

    private static String def(String v, String d) {
        return v == null || v.isEmpty() ? d : v;
    }
}

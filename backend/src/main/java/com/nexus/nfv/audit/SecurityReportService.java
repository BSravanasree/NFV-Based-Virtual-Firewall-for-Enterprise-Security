package com.nexus.nfv.audit;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.nexus.nfv.alert.AlertService;
import com.nexus.nfv.alert.SecurityAlert;
import com.nexus.nfv.firewall.FirewallRule;
import com.nexus.nfv.firewall.FirewallRuleRepository;
import com.nexus.nfv.security.CurrentUser;
import com.nexus.nfv.traffic.TrafficDtos;
import com.nexus.nfv.traffic.TrafficService;
import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Generates the PDF security report (posture, VNFs, policy, traffic, alerts, recent audit trail). */
@Service
public class SecurityReportService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);
    private static final Color NAVY = new Color(15, 23, 42);
    private static final Color HEADER = new Color(226, 232, 240);

    private final VnfRepository vnfs;
    private final FirewallRuleRepository rules;
    private final TrafficService traffic;
    private final AlertService alerts;
    private final AuditService audit;

    public SecurityReportService(VnfRepository vnfs, FirewallRuleRepository rules, TrafficService traffic,
                                 AlertService alerts, AuditService audit) {
        this.vnfs = vnfs;
        this.rules = rules;
        this.traffic = traffic;
        this.alerts = alerts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public byte[] generate(int hours) {
        Duration window = Duration.ofHours(Math.max(1, Math.min(hours, 168)));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 42, 36);
        PdfWriter.getInstance(doc, bos);
        doc.open();

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, NAVY);
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, NAVY);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font small = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.DARK_GRAY);

        doc.add(new Paragraph("NEXUS NFV – Security Report", title));
        doc.add(new Paragraph("Generated " + TS.format(Instant.now()) + " by " + CurrentUser.username()
                + " · reporting window: last " + window.toHours() + " h", small));
        doc.add(new Paragraph("Data provenance: LAB = measured in the local NFV lab, LIVE = production infrastructure, "
                + "DEMO = fixture data. Traffic figures are nftables counters and NFLOG header events (no payload inspection).", small));
        doc.add(Chunk.NEWLINE);

        // VNFs
        doc.add(new Paragraph("1. Firewall VNFs", h2));
        List<Vnf> all = vnfs.findAllByOrderByCreatedAtDesc();
        PdfPTable t = table(new float[]{2.2f, 1.3f, 1.2f, 1.6f, 1.6f, 1.2f, 1.3f},
                "Name", "State", "Health", "LAN IP", "DMZ IP", "Default", "Policy sync");
        for (Vnf v : all) {
            cells(t, body, v.getName(), v.getState(), v.getHealth(), v.getLanIp(), v.getWanIp(), v.getDefaultAction(),
                    v.getSyncStatus() + " r" + v.getAppliedRevision() + "/" + v.getRulesRevision());
        }
        doc.add(t);

        // Rules
        doc.add(new Paragraph("2. Firewall policy", h2));
        for (Vnf v : all) {
            List<FirewallRule> rl = rules.findByVnfIdOrderByPriorityAsc(v.getId());
            if (rl.isEmpty()) continue;
            doc.add(new Paragraph(v.getName() + " (default " + v.getDefaultAction() + ")", body));
            PdfPTable r = table(new float[]{0.8f, 2f, 0.9f, 0.9f, 2f, 2f, 1.2f, 1.2f},
                    "Prio", "Name", "Action", "Proto", "Source", "Destination", "Status", "Hits (pkts)");
            for (FirewallRule x : rl) {
                cells(r, body, x.getPriority(), x.getName(), x.getAction(), x.getProtocol(),
                        nz(x.getSourceCidr()) + (x.getSourcePort() == null ? "" : ":" + x.getSourcePort()),
                        nz(x.getDestinationCidr()) + (x.getDestinationPort() == null ? "" : ":" + x.getDestinationPort()),
                        x.isEnabled() ? "enabled" : "disabled", x.getHitPackets());
            }
            doc.add(r);
        }

        // Traffic
        doc.add(new Paragraph("3. Traffic (" + window.toHours() + " h)", h2));
        TrafficDtos.Summary s = traffic.summary(window, null);
        PdfPTable tr = table(new float[]{3, 2}, "Metric", "Value");
        cells(tr, body, "Counter samples", s.samples());
        cells(tr, body, "Allowed packets / bytes", s.allowedPackets() + " / " + s.allowedBytes());
        cells(tr, body, "Blocked packets / bytes", s.blockedPackets() + " / " + s.blockedBytes());
        cells(tr, body, "TCP / UDP / ICMP packets", s.tcpPackets() + " / " + s.udpPackets() + " / " + s.icmpPackets());
        cells(tr, body, "Inbound / outbound bytes", s.inboundBytes() + " / " + s.outboundBytes());
        cells(tr, body, "Logged blocked events", s.blockedEvents());
        cells(tr, body, "Data sources", s.dataSources().isEmpty() ? "no data" : s.dataSources().toString());
        doc.add(tr);
        TrafficDtos.TopTalkers top = traffic.top(window, 10);
        if (!top.blockedSources().isEmpty()) {
            doc.add(new Paragraph("Top blocked sources", body));
            PdfPTable tb = table(new float[]{3, 1}, "Source IP", "Blocked events");
            top.blockedSources().forEach(k -> cells(tb, body, k.key(), k.count()));
            doc.add(tb);
        }

        // Alerts
        doc.add(new Paragraph("4. Security alerts", h2));
        AlertService.AlertStats as = alerts.stats();
        doc.add(new Paragraph("Open: " + as.open() + " · Acknowledged: " + as.acknowledged()
                + " · Active by severity: " + as.activeBySeverity(), body));
        var list = alerts.export(new AlertService.AlertQuery(null, null, null, null, Instant.now().minus(window), null));
        PdfPTable ta = table(new float[]{1.4f, 1f, 1.3f, 1.8f, 1.6f, 4f}, "Alert", "Severity", "Status", "Type", "Source", "Description");
        list.stream().limit(60).forEach(a -> cells(ta, body, a.alertId(), a.severity(), a.status(), a.eventType(),
                a.sourceIp(), a.description()));
        doc.add(ta);
        if (list.stream().anyMatch(a -> a.severity() == SecurityAlert.Severity.CRITICAL)) {
            doc.add(new Paragraph("Critical alerts present – immediate investigation recommended.",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(185, 28, 28))));
        }

        // Audit
        doc.add(new Paragraph("5. Administrative audit trail (latest 40)", h2));
        var audits = audit.export(new AuditService.AuditQuery(Instant.now().minus(window), null, null, null, null, null, null), 40);
        PdfPTable tu = table(new float[]{2.2f, 1.3f, 2.4f, 1f, 4f}, "Time", "User", "Action", "Outcome", "Details");
        audits.forEach(a -> cells(tu, body, TS.format(a.occurredAt()), a.username(), a.action(), a.outcome(), a.details()));
        doc.add(tu);

        doc.close();
        return bos.toByteArray();
    }

    private static PdfPTable table(float[] widths, String... headers) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        t.setSpacingAfter(10);
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, NAVY);
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, f));
            c.setBackgroundColor(HEADER);
            c.setPadding(4);
            t.addCell(c);
        }
        t.setHeaderRows(1);
        return t;
    }

    private static void cells(PdfPTable t, Font f, Object... values) {
        for (Object v : values) {
            PdfPCell c = new PdfPCell(new Phrase(v == null ? "-" : String.valueOf(v), f));
            c.setPadding(3);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            t.addCell(c);
        }
    }

    private static String nz(String s) {
        return s == null ? "any" : s;
    }
}

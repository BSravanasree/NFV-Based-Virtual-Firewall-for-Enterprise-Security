package com.nexus.nfv.netconf.client;

import com.nexus.nfv.common.exception.InfrastructureException;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelSubsystem;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal, dependency-light NETCONF 1.0 client session (RFC 6241) over the SSH "netconf" subsystem
 * (RFC 6242). The client advertises only base:1.0 so the end-of-message framing ("]]>]]>") is used.
 *
 * <p>Host key policy: trust-on-first-use. The caller passes the pinned fingerprint (or null on first
 * contact); a mismatch aborts the connection before authentication.</p>
 */
public final class NetconfSession implements AutoCloseable {

    public static final String BASE_NS = "urn:ietf:params:xml:ns:netconf:base:1.0";
    private static final String EOM = "]]>]]>";
    private static final int MAX_REPLY_BYTES = 16 * 1024 * 1024;

    private final SshClient client;
    private final ClientSession session;
    private final ChannelSubsystem channel;
    private final InputStream in;
    private final OutputStream out;
    private final Duration timeout;
    private final AtomicInteger messageId = new AtomicInteger(100);
    private final List<String> serverCapabilities = new ArrayList<>();
    private final String sessionId;
    private final String hostKeyFingerprint;

    private NetconfSession(SshClient client, ClientSession session, ChannelSubsystem channel, Duration timeout,
                           String fingerprint) throws IOException {
        this.client = client;
        this.session = session;
        this.channel = channel;
        this.in = channel.getInvertedOut();
        this.out = channel.getInvertedIn();
        this.timeout = timeout;
        this.hostKeyFingerprint = fingerprint;
        send("<?xml version=\"1.0\" encoding=\"UTF-8\"?><hello xmlns=\"" + BASE_NS + "\"><capabilities>"
                + "<capability>urn:ietf:params:netconf:base:1.0</capability></capabilities></hello>");
        Document hello = Xml.parse(readMessage());
        NodeList caps = hello.getElementsByTagNameNS(BASE_NS, "capability");
        for (int i = 0; i < caps.getLength(); i++) serverCapabilities.add(caps.item(i).getTextContent().trim());
        NodeList sid = hello.getElementsByTagNameNS(BASE_NS, "session-id");
        this.sessionId = sid.getLength() > 0 ? sid.item(0).getTextContent().trim() : null;
    }

    public static NetconfSession open(String host, int port, String username, String password,
                                      String pinnedFingerprint, Duration timeout) {
        AtomicReference<String> seen = new AtomicReference<>();
        // one client per session so the host-key verifier is bound before key exchange starts
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((clientSession, remote, key) -> {
            String fp = KeyUtils.getFingerPrint(key);
            seen.set(fp);
            return pinnedFingerprint == null || pinnedFingerprint.equals(fp);
        });
        client.start();
        ClientSession s = null;
        try {
            s = client.connect(username, host, port).verify(timeout).getSession();
            s.addPasswordIdentity(password);
            s.auth().verify(timeout);
            ChannelSubsystem ch = s.createSubsystemChannel("netconf");
            ch.open().verify(timeout);
            return new NetconfSession(client, s, ch, timeout, seen.get());
        } catch (IOException | RuntimeException ex) {
            closeQuietly(s);
            client.stop();
            if (pinnedFingerprint != null && seen.get() != null && !pinnedFingerprint.equals(seen.get())) {
                throw new InfrastructureException("NETCONF", "SSH host key of " + host
                        + " changed (expected " + pinnedFingerprint + ", got " + seen.get()
                        + "). Connection refused – verify the device and reset the pinned key.");
            }
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (msg.toLowerCase().contains("auth")) {
                throw new InfrastructureException("NETCONF", "authentication to " + host + ":" + port + " failed");
            }
            throw new InfrastructureException("NETCONF", "cannot connect to " + host + ":" + port + " (" + msg + ")", ex);
        }
    }

    public List<String> capabilities() {
        return serverCapabilities;
    }

    public String sessionId() {
        return sessionId;
    }

    public String hostKeyFingerprint() {
        return hostKeyFingerprint;
    }

    public boolean supports(String capabilityPrefix) {
        return serverCapabilities.stream().anyMatch(c -> c.startsWith(capabilityPrefix));
    }

    // ------------------------------------------------------------------ operations

    /** &lt;get-config&gt; with an optional subtree filter. Returns the serialized children of &lt;data&gt;. */
    public String getConfig(String source, String subtreeFilter) {
        String filter = subtreeFilter == null ? "" : "<filter type=\"subtree\">" + subtreeFilter + "</filter>";
        Document reply = rpc("<get-config><source><" + datastore(source) + "/></source>" + filter + "</get-config>");
        NodeList data = reply.getElementsByTagNameNS(BASE_NS, "data");
        return data.getLength() == 0 ? "" : Xml.innerXml((Element) data.item(0));
    }

    public void editConfig(String target, String configInnerXml, String defaultOperation) {
        rpc("<edit-config><target><" + datastore(target) + "/></target>"
                + (defaultOperation == null ? "" : "<default-operation>" + defaultOperation + "</default-operation>")
                + "<test-option>test-then-set</test-option><error-option>rollback-on-error</error-option>"
                + "<config>" + configInnerXml + "</config></edit-config>");
    }

    public void validate(String source) {
        rpc("<validate><source><" + datastore(source) + "/></source></validate>");
    }

    public void commit() {
        rpc("<commit/>");
    }

    public void discardChanges() {
        rpc("<discard-changes/>");
    }

    public void lock(String target) {
        rpc("<lock><target><" + datastore(target) + "/></target></lock>");
    }

    public void unlock(String target) {
        rpc("<unlock><target><" + datastore(target) + "/></target></unlock>");
    }

    /** Sends one &lt;rpc&gt; and returns the parsed reply; any &lt;rpc-error&gt; is raised. */
    public synchronized Document rpc(String operationXml) {
        String id = String.valueOf(messageId.incrementAndGet());
        try {
            send("<?xml version=\"1.0\" encoding=\"UTF-8\"?><rpc message-id=\"" + id + "\" xmlns=\"" + BASE_NS + "\">"
                    + operationXml + "</rpc>");
            Document reply = Xml.parse(readMessage());
            NodeList errors = reply.getElementsByTagNameNS(BASE_NS, "rpc-error");
            if (errors.getLength() > 0) {
                Element e = (Element) errors.item(0);
                String sev = Xml.childText(e, "error-severity");
                if (!"warning".equals(sev)) {
                    throw new InfrastructureException("NETCONF", "rpc-error " + Xml.childText(e, "error-tag")
                            + ": " + Xml.childText(e, "error-message")
                            + (Xml.childText(e, "error-path").isEmpty() ? "" : " at " + Xml.childText(e, "error-path")));
                }
            }
            return reply;
        } catch (IOException ex) {
            throw new InfrastructureException("NETCONF", "transport error: " + ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------------ framing

    private void send(String xml) throws IOException {
        out.write((xml + "\n" + EOM).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private String readMessage() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] eom = EOM.getBytes(StandardCharsets.US_ASCII);
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int matched = 0;
        while (true) {
            if (System.currentTimeMillis() > deadline) throw new IOException("timeout waiting for NETCONF reply");
            int b = in.read();
            if (b < 0) throw new IOException("NETCONF session closed by server");
            buf.write(b);
            matched = (b == eom[matched]) ? matched + 1 : (b == eom[0] ? 1 : 0);
            if (matched == eom.length) break;
            if (buf.size() > MAX_REPLY_BYTES) throw new IOException("NETCONF reply exceeds size limit");
        }
        String s = buf.toString(StandardCharsets.UTF_8);
        return s.substring(0, s.length() - EOM.length()).trim();
    }

    private static String datastore(String name) {
        return switch (name) {
            case "running", "candidate", "startup" -> name;
            default -> throw new IllegalArgumentException("Unsupported datastore " + name);
        };
    }

    @Override
    public void close() {
        try {
            rpc("<close-session/>");
        } catch (RuntimeException ignored) {
            // best effort
        }
        try {
            channel.close(true);
        } catch (RuntimeException ignored) {
            // ignore
        }
        closeQuietly(session);
        client.stop();
    }

    private static void closeQuietly(ClientSession s) {
        if (s != null) {
            try {
                s.close(true);
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
    }
}

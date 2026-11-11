package com.nexus.nfv.common.net;

import com.nexus.nfv.common.exception.ApiException;

import java.util.regex.Pattern;

/**
 * Strict IPv4 / CIDR / port-range validation and normalisation. All values that end up in firewall rules,
 * OpenFlow selectors or NETCONF payloads pass through here; nothing is resolved via DNS.
 */
public final class Ipv4 {

    private static final Pattern OCTETS = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern PORT_SPEC = Pattern.compile("^\\d{1,5}(-\\d{1,5})?$");

    private Ipv4() {
    }

    public static boolean isAddress(String s) {
        return s != null && OCTETS.matcher(s).matches();
    }

    public static long toLong(String ip) {
        if (!isAddress(ip)) throw ApiException.badRequest("Invalid IPv4 address: " + ip);
        long v = 0;
        for (String o : ip.split("\\.")) v = (v << 8) | Integer.parseInt(o);
        return v;
    }

    public static String fromLong(long v) {
        return ((v >> 24) & 0xFF) + "." + ((v >> 16) & 0xFF) + "." + ((v >> 8) & 0xFF) + "." + (v & 0xFF);
    }

    /**
     * Normalises "a.b.c.d" or "a.b.c.d/n" into canonical network form ("a.b.c.d/32", "10.0.0.0/24").
     * Returns null for null/blank/"any". Rejects host bits set outside the prefix to avoid ambiguous intent.
     */
    public static String normalizeCidr(String input, String field) {
        if (input == null || input.isBlank() || "any".equalsIgnoreCase(input.trim())) return null;
        String s = input.trim();
        String addr = s;
        int prefix = 32;
        int slash = s.indexOf('/');
        if (slash >= 0) {
            addr = s.substring(0, slash);
            try {
                prefix = Integer.parseInt(s.substring(slash + 1));
            } catch (NumberFormatException e) {
                throw ApiException.badRequest(field + ": invalid prefix length in " + s);
            }
            if (prefix < 0 || prefix > 32) throw ApiException.badRequest(field + ": prefix must be 0-32");
        }
        if (!isAddress(addr)) throw ApiException.badRequest(field + ": invalid IPv4 address " + addr);
        long ip = toLong(addr);
        long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        if ((ip & ~mask & 0xFFFFFFFFL) != 0) {
            throw ApiException.badRequest(field + ": " + s + " has host bits set; did you mean "
                    + fromLong(ip & mask) + "/" + prefix + "?");
        }
        return fromLong(ip & mask) + "/" + prefix;
    }

    public static boolean contains(String cidr, String ip) {
        String[] p = cidr.split("/");
        int prefix = Integer.parseInt(p[1]);
        long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (toLong(p[0]) & mask) == (toLong(ip) & mask);
    }

    /** Validates "80" or "1000-2000" (1..65535, lo <= hi). Returns normalised form or null. */
    public static String normalizePortSpec(String input, String field) {
        if (input == null || input.isBlank() || "any".equalsIgnoreCase(input.trim())) return null;
        String s = input.trim();
        if (!PORT_SPEC.matcher(s).matches()) {
            throw ApiException.badRequest(field + ": expected a port (e.g. 443) or range (e.g. 1000-2000)");
        }
        String[] parts = s.split("-");
        int lo = Integer.parseInt(parts[0]);
        int hi = parts.length == 2 ? Integer.parseInt(parts[1]) : lo;
        if (lo < 1 || hi > 65535 || lo > hi) {
            throw ApiException.badRequest(field + ": ports must be within 1-65535 and the range ascending");
        }
        return lo == hi ? String.valueOf(lo) : lo + "-" + hi;
    }
}

# NETCONF Integration

## Server side (inside every firewall VNF)

* **Netopeer2** NETCONF server + **sysrepo** datastore, built from pinned upstream sources:
  libyang `v6.4.13`, libnetconf2 `v4.6.4`, sysrepo `v5.2.27`, netopeer2 commit `92e18139` (2.8.21).
* YANG module **`nexus-firewall`** (namespace `urn:nexus:nfv:firewall`), installed with `sysrepoctl -i`.
* First boot (`netconf/init-netconf.sh`):
  * generates a **per-instance SSH host key** (never baked into the image),
  * creates system user `nexus` with the random password supplied by the orchestrator,
  * configures the SSH endpoint on port 830 (keyboard-interactive, system authentication),
  * NACM: read/write/exec **default deny**, group `nexus-admins` (user `nexus`) permitted.
* **Datastore ↔ nftables bridge** (agent thread):
  * running datastore changes (edit-config / commit) are detected every 3 s, validated and applied to nftables
    (`rulesSource = netconf`);
  * REST-applied policies are exported into the running datastore, so `get-config` always reflects the
    enforced policy.

### YANG model (abridged)

```yang
container firewall {
  leaf default-action { type verdict; default drop; }      // accept | drop
  leaf revision { type uint64; }
  list rule {
    key id;  unique priority;  max-elements 2000;
    leaf id { type uint32 { range "1..max"; } }
    leaf name { type string { length "1..64"; } }
    leaf priority { type uint32 { range "1..65535"; } mandatory true; }
    leaf action { type verdict; mandatory true; }
    leaf protocol { type enumeration { enum any; enum tcp; enum udp; enum icmp; } }
    leaf source-prefix      { type inet:ipv4-prefix; }
    leaf destination-prefix { type inet:ipv4-prefix; }
    leaf source-port      { when "../protocol = 'tcp' or ../protocol = 'udp'"; type port-spec; }
    leaf destination-port { when "../protocol = 'tcp' or ../protocol = 'udp'"; type port-spec; }
    leaf enabled { type boolean; default true; }
    leaf log { type boolean; default true; }
  }
}
```

Constraints (types, ranges, `unique`, `when`) are enforced by sysrepo/libyang on `validate` and `commit`.

## Client side (backend)

`netconf/client/NetconfSession` is a small RFC 6241 client over the RFC 6242 SSH subsystem (Apache MINA SSHD):

* `<hello>` with `base:1.0` (end-of-message framing), server capabilities recorded on the device
* operations: `get-config` (subtree filter), `edit-config` (`test-then-set`, `rollback-on-error`), `validate`,
  `lock` / `unlock`, `commit`, `discard-changes`, `close-session`
* `rpc-error` → exception with error-tag / message / path
* **trust-on-first-use host key pinning** (SHA-256 fingerprint stored on the device; mismatch aborts before
  authentication; admins can reset the pin)
* XML parsing with DOCTYPE/external entities disabled (XXE-safe)

## Platform features

| Feature | Endpoint |
|---|---|
| Device registry (password AES-256-GCM encrypted, never returned) | `GET/POST/DELETE /api/v1/netconf/devices` |
| Auto-registration of lab VNFs with their generated credential | on `VnfEvents.Activated` |
| Connection test (`<hello>`, capabilities, host key pin) | `POST …/{id}/test` |
| Retrieve running configuration | `GET …/{id}/running` |
| Desired configuration (generated from the VNF's rules) | `GET …/{id}/desired` |
| Validate (candidate edit + `validate` + discard) | `POST …/{id}/validate` |
| Push (lock → discard → edit-config candidate → validate → commit → unlock; fallback to running) | `POST …/{id}/push` |
| Sync check (canonical checksum of running vs desired / last applied) → `IN_SYNC` / `DRIFT` | `GET …/{id}/sync` |
| Configuration history (every push, rollback, snapshot with XML + checksum) | `GET …/{id}/versions` |
| Rollback to any applied/retrieved version | `POST …/{id}/versions/{v}/rollback` |

Canonical checksums ignore ordering, whitespace and the `revision` leaf, so "drift" means a real difference
in enforced policy.

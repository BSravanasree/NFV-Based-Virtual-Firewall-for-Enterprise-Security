-- Trust-on-first-use pinning of the NETCONF server SSH host key (SHA-256 fingerprint).
ALTER TABLE netconf_devices ADD COLUMN host_key_fingerprint VARCHAR(128) NULL;

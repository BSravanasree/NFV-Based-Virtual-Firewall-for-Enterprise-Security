#!/usr/bin/env bash
# First-boot NETCONF provisioning of a firewall VNF instance (idempotent).
#  * generates a per-instance SSH host key (never baked into the image)
#  * creates the 'nexus' system user with the password supplied by the orchestrator
#  * configures the Netopeer2 SSH endpoint (port 830) for that user (keyboard-interactive, system auth)
#  * grants the user NACM write access (group nexus-admins)
set -euo pipefail

SYSREPOCFG=/usr/local/bin/sysrepocfg
SCRIPTS=/opt/netopeer2/scripts
NC_USER=nexus

if [[ -z "${NETCONF_PASSWORD:-}" || ${#NETCONF_PASSWORD} -lt 12 ]]; then
  echo "init-netconf: NETCONF_PASSWORD missing or too short; NETCONF disabled" >&2
  exit 1
fi

# 1. per-instance host key (merge_hostkey.sh only generates when no 'genkey' exists)
if [[ ! -f /var/lib/nexus-agent/.netconf-provisioned ]]; then
  SYSREPOCFG_EXECUTABLE=$SYSREPOCFG OPENSSL_EXECUTABLE=/usr/bin/openssl bash "$SCRIPTS/merge_hostkey.sh"
fi

# 2. system user (password set on every boot so credential rotation via redeploy works)
id -u "$NC_USER" >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin "$NC_USER"
printf '%s:%s\n' "$NC_USER" "$NETCONF_PASSWORD" | chpasswd

# 3. listen endpoint + client authentication
cat > /tmp/nc-server.xml <<EOF
<netconf-server xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-server">
  <listen>
    <endpoints>
      <endpoint>
        <name>default-ssh</name>
        <ssh>
          <tcp-server-parameters>
            <local-bind>
              <local-address>0.0.0.0</local-address>
              <local-port>830</local-port>
            </local-bind>
          </tcp-server-parameters>
          <ssh-server-parameters>
            <server-identity>
              <host-key>
                <name>default-key</name>
                <public-key>
                  <central-keystore-reference>genkey</central-keystore-reference>
                </public-key>
              </host-key>
            </server-identity>
            <client-authentication>
              <users>
                <user>
                  <name>${NC_USER}</name>
                  <keyboard-interactive xmlns="urn:cesnet:libnetconf2-netconf-server">
                    <use-system-auth/>
                  </keyboard-interactive>
                </user>
              </users>
            </client-authentication>
          </ssh-server-parameters>
        </ssh>
      </endpoint>
    </endpoints>
  </listen>
</netconf-server>
EOF
$SYSREPOCFG --edit=/tmp/nc-server.xml -d startup -f xml -m ietf-netconf-server -v1

# 4. NACM: nexus-admins may read/write/exec everything
cat > /tmp/nacm.xml <<EOF
<nacm xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-acm">
  <enable-nacm>true</enable-nacm>
  <read-default>deny</read-default>
  <write-default>deny</write-default>
  <exec-default>deny</exec-default>
  <groups>
    <group>
      <name>nexus-admins</name>
      <user-name>${NC_USER}</user-name>
    </group>
  </groups>
  <rule-list>
    <name>nexus-admins-all</name>
    <group>nexus-admins</group>
    <rule>
      <name>permit-all</name>
      <module-name>*</module-name>
      <access-operations>*</access-operations>
      <action>permit</action>
    </rule>
  </rule-list>
</nacm>
EOF
$SYSREPOCFG --edit=/tmp/nacm.xml -d startup -f xml -m ietf-netconf-acm -v1
rm -f /tmp/nc-server.xml /tmp/nacm.xml

# copy startup -> running for the modules we touched
for m in ietf-keystore ietf-netconf-server ietf-netconf-acm; do
  $SYSREPOCFG -C startup -d running -m "$m" -v1
done
touch /var/lib/nexus-agent/.netconf-provisioned
echo "init-netconf: NETCONF provisioned for user '${NC_USER}'"

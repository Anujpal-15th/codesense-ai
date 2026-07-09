#!/usr/bin/env bash
#
# provision-sandbox-network.sh
#
# Run ONCE on the Linux deployment host before enabling the Docker execution
# sandbox (EXECUTION_SANDBOX_TYPE=docker). Creates the bridge network the
# sandbox containers join and installs the host-level egress-blocking firewall
# rule that makes them network-isolated.
#
# WHY THIS EXISTS: `docker run --network none` (or --internal) blocks a
# container's outbound access but ALSO disables the `-p` port publishing the
# JDWP/JDI debug attach needs - Docker publishes ports over the same bridge/NAT
# path those modes remove. So the sandbox uses a normal bridge network (so -p
# works) and blocks egress with an iptables rule in the DOCKER-USER chain
# instead. Without this rule the container has FULL internet access.
#
# LINUX ONLY. On Docker Desktop (macOS/Windows) the daemon runs in a managed VM
# where DOCKER-USER is not cleanly reachable; use local-process there. See
# CLAUDE.md ("Docker execution sandbox — Linux deployment").
#
# Idempotent: safe to re-run. Requires root (docker + iptables).

set -euo pipefail

NET_NAME="${EXECUTION_SANDBOX_NETWORK:-codesense-sandbox-net}"
SUBNET="${EXECUTION_SANDBOX_SUBNET:-172.31.253.0/24}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "ERROR: run as root (needs docker + iptables)." >&2
  exit 1
fi

if ! command -v iptables >/dev/null 2>&1; then
  echo "ERROR: iptables not found. This host cannot enforce sandbox egress blocking." >&2
  exit 1
fi

# 1. Bridge network with a fixed subnet (so the iptables rule can target it).
if docker network inspect "$NET_NAME" >/dev/null 2>&1; then
  echo "network '$NET_NAME' already exists - leaving as-is"
else
  echo "creating bridge network '$NET_NAME' (subnet $SUBNET)"
  docker network create --driver bridge --subnet "$SUBNET" "$NET_NAME"
fi

# 2. DOCKER-USER egress-drop rule.
#
# DOCKER-USER is consulted before Docker's own forwarding rules and is the
# supported hook for custom container firewalling. We drop NEW/INVALID
# connections whose SOURCE is the sandbox subnet - i.e. container-INITIATED
# outbound. Return traffic for the inbound published JDWP port is ESTABLISHED
# (not NEW), so it is NOT matched and the host->container debug attach keeps
# working. The debuggee is a JDWP *server*; it never initiates connections, so
# nothing legitimate is blocked.
#
# NOTE: this drops ALL container-initiated egress including DNS - intended
# (untrusted code gets zero network). Verify on THIS host after running; the
# exact rule can vary with the host's iptables/nftables setup.
RULE=(DOCKER-USER -s "$SUBNET" -m conntrack --ctstate NEW,INVALID -j DROP)

if iptables -C "${RULE[@]}" 2>/dev/null; then
  echo "egress-drop rule already present"
else
  echo "installing egress-drop rule for $SUBNET in DOCKER-USER"
  iptables -I "${RULE[@]}"
fi

cat <<EOF

Done. Sandbox network '$NET_NAME' ($SUBNET) is provisioned with egress blocking.

VERIFY on this host before trusting isolation:

  # (a) egress is blocked (should time out / fail):
  docker run --rm --network $NET_NAME codesense/execution-sandbox:latest \\
    --entrypoint sh -c 'wget -T4 -qO- http://1.1.1.1 && echo REACHED || echo BLOCKED'

  # (b) the JDWP port still publishes to host loopback (should be reachable):
  #     launch a container with -p 127.0.0.1:<port>:5005 and confirm the host
  #     can connect to 127.0.0.1:<port> while the container runs.

To make the iptables rule survive reboot, persist it with your distro's
mechanism (e.g. netfilter-persistent / iptables-save), or re-run this script
from a systemd unit before the app starts.
EOF

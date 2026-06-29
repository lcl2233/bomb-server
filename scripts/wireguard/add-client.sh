#!/bin/bash
# WireGuard 添加客户端（支付开通 VPN 时由 bomb-server SSH 调用）
# 用法: add-client.sh <client_name> [ip_suffix]
# 示例: add-client.sh u1
set -euo pipefail

WG_DIR="/etc/wireguard"
SERVER_PUBLIC_KEY_FILE="${WG_DIR}/server_public.key"
WG_CONF="${WG_DIR}/wg0.conf"
ENDPOINT="64.177.116.240:51820"
DNS="1.1.1.1"
SUBNET_PREFIX="10.8.0"

usage() {
    echo "Usage: $0 <client_name> [ip_suffix]"
    echo ""
    echo "  client_name   Client identifier, e.g. u1, u2, phone"
    echo "  ip_suffix     Optional VPN host suffix, e.g. 3 -> 10.8.0.3/32"
    echo "                If omitted, the next free IP in ${SUBNET_PREFIX}.0/24 is used"
    exit 1
}

[[ $# -lt 1 ]] && usage

CLIENT_NAME="$1"

if [[ ! "$CLIENT_NAME" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    echo "Error: invalid client name '$CLIENT_NAME' (use letters, numbers, _ or -)"
    exit 1
fi

PRIVATE_KEY="${WG_DIR}/${CLIENT_NAME}_private.key"
PUBLIC_KEY="${WG_DIR}/${CLIENT_NAME}_public.key"
CLIENT_CONF="${WG_DIR}/${CLIENT_NAME}.conf"

if [[ -f "$PRIVATE_KEY" || -f "$PUBLIC_KEY" || -f "$CLIENT_CONF" ]]; then
    echo "Error: client '$CLIENT_NAME' already exists"
    exit 1
fi

if [[ ! -f "$SERVER_PUBLIC_KEY_FILE" ]]; then
    echo "Error: missing $SERVER_PUBLIC_KEY_FILE"
    exit 1
fi

if [[ ! -f "$WG_CONF" ]]; then
    echo "Error: missing $WG_CONF"
    exit 1
fi

if [[ $# -ge 2 ]]; then
    IP_SUFFIX="$2"
    if [[ ! "$IP_SUFFIX" =~ ^[0-9]+$ ]] || [[ "$IP_SUFFIX" -lt 2 || "$IP_SUFFIX" -gt 254 ]]; then
        echo "Error: ip_suffix must be between 2 and 254"
        exit 1
    fi
else
    used_ips=$(grep -E '^AllowedIPs = '"${SUBNET_PREFIX}"'\.[0-9]+/32' "$WG_CONF" \
        | sed -E 's|^AllowedIPs = '"${SUBNET_PREFIX}"'\.([0-9]+)/32|\1|' \
        | sort -n)
    IP_SUFFIX=""
    for i in $(seq 2 254); do
        if ! echo "$used_ips" | grep -qx "$i"; then
            IP_SUFFIX=$i
            break
        fi
    done
    if [[ -z "$IP_SUFFIX" ]]; then
        echo "Error: no free VPN IP available in ${SUBNET_PREFIX}.0/24"
        exit 1
    fi
fi

VPN_IP="${SUBNET_PREFIX}.${IP_SUFFIX}"

if grep -qE "^AllowedIPs = ${VPN_IP}/32" "$WG_CONF"; then
    echo "Error: VPN IP ${VPN_IP}/32 is already assigned"
    exit 1
fi

wg genkey | tee "$PRIVATE_KEY" | wg pubkey > "$PUBLIC_KEY"
chmod 600 "$PRIVATE_KEY" "$PUBLIC_KEY"

CLIENT_PUB=$(cat "$PUBLIC_KEY")
SERVER_PUB=$(cat "$SERVER_PUBLIC_KEY_FILE")
CLIENT_PRIV=$(cat "$PRIVATE_KEY")

cat >> "$WG_CONF" <<EOF

[Peer]
PublicKey = ${CLIENT_PUB}
AllowedIPs = ${VPN_IP}/32
EOF

cat > "$CLIENT_CONF" <<EOF
[Interface]
PrivateKey = ${CLIENT_PRIV}
Address = ${VPN_IP}/24
DNS = ${DNS}

[Peer]
PublicKey = ${SERVER_PUB}
Endpoint = ${ENDPOINT}
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
EOF

chmod 600 "$CLIENT_CONF"

if ip link show wg0 >/dev/null 2>&1; then
    wg syncconf wg0 <(wg-quick strip wg0)
fi

echo "Client '${CLIENT_NAME}' created successfully."
echo "  Private key: ${PRIVATE_KEY}"
echo "  Public key:  ${PUBLIC_KEY}"
echo "  Config file: ${CLIENT_CONF}"
echo "  VPN IP:      ${VPN_IP}/32"
echo ""
echo "=== ${CLIENT_CONF} ==="
cat "$CLIENT_CONF"

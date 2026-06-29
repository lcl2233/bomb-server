#!/bin/bash
# WireGuard 删除客户端（会员过期 revoke 时由 bomb-server SSH 调用）
# 用法: remove-client.sh <client_name>
# 示例: remove-client.sh u1
set -euo pipefail

WG_DIR="/etc/wireguard"
WG_CONF="${WG_DIR}/wg0.conf"

usage() {
    echo "Usage: $0 <client_name>"
    echo ""
    echo "  client_name   Client identifier, e.g. u1, u2"
    echo ""
    echo "Examples:"
    echo "  $0 u1"
    exit 1
}

[[ $# -lt 1 ]] && usage

CLIENT_NAME="$1"
PUB_FILE="${WG_DIR}/${CLIENT_NAME}_public.key"
PRIV_FILE="${WG_DIR}/${CLIENT_NAME}_private.key"
CLIENT_CONF="${WG_DIR}/${CLIENT_NAME}.conf"

if [[ ! "$CLIENT_NAME" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    echo "Error: invalid client name '$CLIENT_NAME' (use letters, numbers, _ or -)"
    exit 1
fi

if [[ ! -f "$PUB_FILE" ]]; then
    echo "Client '${CLIENT_NAME}' not found on server, nothing to remove"
    exit 0
fi

PUBLIC_KEY=$(cat "$PUB_FILE")

if [[ -f "$WG_CONF" ]]; then
    awk -v pk="$PUBLIC_KEY" '
        function flush(block, drop) {
            if (block != "" && !drop) {
                printf "%s", block
            }
            return ""
        }
        /^\[Peer\]/ {
            block = flush(block, drop) $0 "\n"
            drop = 0
            in_peer = 1
            next
        }
        in_peer {
            block = block $0 "\n"
            if ($1 == "PublicKey" && $3 == pk) {
                drop = 1
            }
            if ($0 == "") {
                block = flush(block, drop)
                in_peer = 0
            }
            next
        }
        {
            block = flush(block, drop)
            print
        }
        END {
            flush(block, drop)
        }
    ' "$WG_CONF" > "${WG_CONF}.tmp"
    mv "${WG_CONF}.tmp" "$WG_CONF"
    chmod 600 "$WG_CONF"
fi

if ip link show wg0 >/dev/null 2>&1; then
    wg syncconf wg0 <(wg-quick strip wg0)
fi

rm -f "$PRIV_FILE" "$PUB_FILE" "$CLIENT_CONF"

echo "Client '${CLIENT_NAME}' removed successfully"
echo "  Public key:  ${PUBLIC_KEY}"
echo "  Removed:     ${PRIV_FILE}, ${PUB_FILE}, ${CLIENT_CONF}"

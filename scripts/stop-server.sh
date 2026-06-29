#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="bomb-server"

systemctl stop "${SERVICE_NAME}"
systemctl --no-pager --full status "${SERVICE_NAME}" || true


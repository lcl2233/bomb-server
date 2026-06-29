#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="bomb-server"

systemctl start "${SERVICE_NAME}"
systemctl --no-pager --full status "${SERVICE_NAME}"


#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_STORE_FILE="${HOME}/secure/agent-control-upload.p12"

export AGENT_CONTROL_UPLOAD_STORE_FILE="${AGENT_CONTROL_UPLOAD_STORE_FILE:-${DEFAULT_STORE_FILE}}"
export AGENT_CONTROL_UPLOAD_KEY_ALIAS="${AGENT_CONTROL_UPLOAD_KEY_ALIAS:-agent-control-upload}"

if [[ ! -f "${AGENT_CONTROL_UPLOAD_STORE_FILE}" ]]; then
  echo "Upload keystore not found: ${AGENT_CONTROL_UPLOAD_STORE_FILE}" >&2
  exit 1
fi

if [[ -z "${AGENT_CONTROL_UPLOAD_STORE_PASSWORD:-}" ]]; then
  printf "Store password: "
  read -rsp "" AGENT_CONTROL_UPLOAD_STORE_PASSWORD
  echo
  export AGENT_CONTROL_UPLOAD_STORE_PASSWORD
fi

if [[ -z "${AGENT_CONTROL_UPLOAD_KEY_PASSWORD:-}" ]]; then
  printf "Key password (press Enter only if same password is accepted by your key): "
  read -rsp "" AGENT_CONTROL_UPLOAD_KEY_PASSWORD
  echo
  if [[ -z "${AGENT_CONTROL_UPLOAD_KEY_PASSWORD}" ]]; then
    AGENT_CONTROL_UPLOAD_KEY_PASSWORD="${AGENT_CONTROL_UPLOAD_STORE_PASSWORD}"
  fi
  export AGENT_CONTROL_UPLOAD_KEY_PASSWORD
fi

echo "Using upload keystore: ${AGENT_CONTROL_UPLOAD_STORE_FILE}"
echo "Using upload key alias: ${AGENT_CONTROL_UPLOAD_KEY_ALIAS}"

cd "${ROOT_DIR}"
./gradlew bundleRelease signingReport

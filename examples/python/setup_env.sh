#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
VENV_PATH="${SCRIPT_DIR}/.venv"

echo "[fidonext] Creating virtual environment at ${VENV_PATH}"
python -m venv "${VENV_PATH}"

echo "[fidonext] Activating virtual environment"
# shellcheck disable=SC1090
source "${VENV_PATH}/bin/activate"

echo "[fidonext] Upgrading pip"
pip install --upgrade pip

echo "[fidonext] Installing Python dependencies"
pip install -r "${SCRIPT_DIR}/requirements.txt"

echo "[fidonext] Environment ready. Activate with:"
echo "source ${VENV_PATH}/bin/activate"


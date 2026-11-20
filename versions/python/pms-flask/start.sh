#!/usr/bin/env sh
set -eu

exec flask --app app:app run --host 127.0.0.1 --port "${SERVER_PORT:-8001}" --debug

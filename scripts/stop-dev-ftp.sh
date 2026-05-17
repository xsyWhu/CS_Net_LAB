#!/usr/bin/env bash
set -euo pipefail

if [[ ! -f logs/dev-ftp.pid ]]; then
  echo "dev FTP server is not running"
  exit 0
fi

pid="$(cat logs/dev-ftp.pid)"
if kill -0 "$pid" 2>/dev/null; then
  kill "$pid"
  echo "stopped dev FTP server with pid $pid"
else
  echo "dev FTP server pid $pid is not active"
fi
rm -f logs/dev-ftp.pid


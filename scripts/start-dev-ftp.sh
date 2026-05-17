#!/usr/bin/env bash
set -euo pipefail

mkdir -p logs
if [[ -f logs/dev-ftp.pid ]] && kill -0 "$(cat logs/dev-ftp.pid)" 2>/dev/null; then
  echo "dev FTP server is already running with pid $(cat logs/dev-ftp.pid)"
  exit 0
fi

nohup python3 tools/dev_ftp_server.py --host 127.0.0.1 --port 2121 --root ftp-root > logs/dev-ftp.log 2>&1 &
echo "$!" > logs/dev-ftp.pid
sleep 0.5

if kill -0 "$(cat logs/dev-ftp.pid)" 2>/dev/null; then
  cat logs/dev-ftp.log
else
  echo "failed to start dev FTP server"
  cat logs/dev-ftp.log
  exit 1
fi


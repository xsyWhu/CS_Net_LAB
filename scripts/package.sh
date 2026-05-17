#!/usr/bin/env bash
set -euo pipefail

./scripts/build.sh
mkdir -p dist
jar --create --file dist/cs-net-lab-ftp-client.jar --main-class com.csnetlab.ftp.FtpClientApp -C out .
echo "created dist/cs-net-lab-ftp-client.jar"

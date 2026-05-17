#!/usr/bin/env bash
set -euo pipefail

mkdir -p out
sources_file="$(mktemp)"
trap 'rm -f "$sources_file"' EXIT

find src/main/java -name '*.java' | sort > "$sources_file"
javac -encoding UTF-8 -d out @"$sources_file"

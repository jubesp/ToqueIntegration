#!/usr/bin/env bash
# Version publiée par ce dépôt : celle de Wuki60 (app/build.gradle.kts) suivie de « .R » (tools/fork/REVISION).
set -euo pipefail
cd "$(dirname "$0")/../.."
v=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts | head -1)
echo "$v.$(tr -d "[:space:]" < tools/fork/REVISION)"

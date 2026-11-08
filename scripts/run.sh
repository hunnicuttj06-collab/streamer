#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
exec mvn -B compile exec:java

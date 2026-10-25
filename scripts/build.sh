#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
(cd frontend && npm ci && npm run build)
./mvnw verify
mkdir -p target/classes/static
cp -R frontend/dist/. target/classes/static/
./mvnw -DskipTests package

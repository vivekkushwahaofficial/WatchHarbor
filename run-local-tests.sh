
#!/usr/bin/env bash
set -e

SERVICE_DIR="services/monitor-service"

if [ ! -f "$SERVICE_DIR/.env" ]; then
    echo "ERROR: $SERVICE_DIR/.env not found."
    exit 1
fi

set -a
source "$SERVICE_DIR/.env"
set +a

if [ -z "${TEST_DB_USERNAME:-}" ] ||
   [ -z "${TEST_DB_PASSWORD:-}" ]; then
    echo "ERROR: Database credentials are missing."
    exit 1
fi

cd "$SERVICE_DIR"
./mvnw.cmd "$@"

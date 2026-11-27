#!/usr/bin/env bash
# Run digitalhub-core on the host, wired to the local Kind cluster and SeaweedFS.
# Usage: deploy/local/run-core.sh [spring-profile]
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${DIR}/../.." && pwd)"
PROFILE="${1:-default}"

cd "${ROOT}"
set -a; source "${DIR}/core.env"; set +a
export KUBECONFIG="${DIR}/.kubeconfig"

[[ -f "${KUBECONFIG}" ]] || { echo "missing ${KUBECONFIG}: run deploy/local/up.sh first"; exit 1; }
[[ -d "${ROOT}/application/target" ]] || ./build.sh

echo ">> running core (profile ${PROFILE}) at ${DH_ENDPOINT}"
./mvnw spring-boot:run -pl application -Dspring-boot.run.profiles="${PROFILE}"

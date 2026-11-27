#!/usr/bin/env bash
# Delete the local Kind cluster (including SeaweedFS data).
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLUSTER_NAME="${CLUSTER_NAME:-dhcore}"

kind delete cluster --name "${CLUSTER_NAME}" --kubeconfig "${DIR}/.kubeconfig"
rm -f "${DIR}/.kubeconfig"

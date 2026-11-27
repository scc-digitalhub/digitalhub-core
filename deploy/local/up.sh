#!/usr/bin/env bash
# Create a local Kind cluster + SeaweedFS for running digitalhub-core on the host.
# Idempotent: safe to re-run.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLUSTER_NAME="${CLUSTER_NAME:-dhcore}"
CORE_HOST="${CORE_HOST:-host.docker.internal}"
KUBECONFIG_FILE="${DIR}/.kubeconfig"

# load namespace / bucket from core.env
set -a; source "${DIR}/core.env"; set +a
export KUBECONFIG="${KUBECONFIG_FILE}"
KCTX="kind-${CLUSTER_NAME}"
k() { kubectl --context "${KCTX}" "$@"; }

for bin in kind kubectl docker; do
  command -v "$bin" >/dev/null || { echo "missing required tool: $bin"; exit 1; }
done

# 1. cluster
if kind get clusters 2>/dev/null | grep -qx "${CLUSTER_NAME}"; then
  echo ">> kind cluster '${CLUSTER_NAME}' already exists"
  kind export kubeconfig --name "${CLUSTER_NAME}" --kubeconfig "${KUBECONFIG_FILE}"
else
  echo ">> creating kind cluster '${CLUSTER_NAME}'"
  kind create cluster --name "${CLUSTER_NAME}" --config "${DIR}/kind-config.yaml" --kubeconfig "${KUBECONFIG_FILE}" --wait 120s
fi

# 2. resolve the host IP as seen from the kind node
NODE="${CLUSTER_NAME}-control-plane"
HOST_IP="$(docker exec "${NODE}" getent ahostsv4 "${CORE_HOST}" 2>/dev/null | awk 'NR==1{print $1}' || true)"
if [[ -z "${HOST_IP}" ]]; then
  # linux native docker: fall back to the IPv4 gateway of the kind network
  HOST_IP="$(docker network inspect kind --format '{{range .IPAM.Config}}{{.Gateway}} {{end}}' | tr ' ' '\n' | grep -E '^[0-9]+\.' | head -1)"
fi
[[ -n "${HOST_IP}" ]] || { echo "could not determine host IP from kind node"; exit 1; }
echo ">> host '${CORE_HOST}' is ${HOST_IP} from inside the cluster"

# 3. CoreDNS: make pods resolve CORE_HOST to the host
COREFILE="$(k -n kube-system get configmap coredns -o jsonpath='{.data.Corefile}')"
COREFILE="$(printf '%s\n' "${COREFILE}" | awk '
  /# dhcore-local-begin/ {skip=1}
  !skip {print}
  /# dhcore-local-end/ {skip=0}
')"
COREFILE="$(printf '%s\n' "${COREFILE}" | awk -v ip="${HOST_IP}" -v host="${CORE_HOST}" '
  /^[[:space:]]*kubernetes / && !done {
    print "    # dhcore-local-begin"
    print "    hosts {"
    print "       " ip " " host
    print "       fallthrough"
    print "    }"
    print "    # dhcore-local-end"
    done=1
  }
  {print}
')"
k -n kube-system create configmap coredns --from-literal=Corefile="${COREFILE}" --dry-run=client -o yaml | k apply -f -
k -n kube-system rollout restart deployment coredns
k -n kube-system rollout status deployment coredns --timeout=120s

# 4. namespace for runs
k create namespace "${K8S_NAMESPACE}" --dry-run=client -o yaml | k apply -f -

# 5. SeaweedFS + bucket
k apply -f "${DIR}/seaweedfs.yaml"
k -n seaweedfs rollout status deployment seaweedfs --timeout=300s
echo ">> ensuring bucket '${S3_BUCKET}'"
weed_shell() { k -n seaweedfs exec deploy/seaweedfs -- sh -c "echo '$1' | weed shell" 2>/dev/null; }
for i in $(seq 1 30); do
  weed_shell "s3.bucket.list" | grep -qw "${S3_BUCKET}" && break
  weed_shell "s3.bucket.create -name ${S3_BUCKET}" >/dev/null || true
  [[ $i == 30 ]] && { echo "failed to create bucket ${S3_BUCKET}"; exit 1; }
  sleep 2
done

# 6. host-side name resolution check
if ! grep -Eq "^[[:space:]]*127\.0\.0\.1[[:space:]].*\b${CORE_HOST}\b" /etc/hosts; then
  echo
  echo "!! '${CORE_HOST}' is not mapped in /etc/hosts. Core (and your browser) need it:"
  echo "     echo '127.0.0.1 ${CORE_HOST}' | sudo tee -a /etc/hosts"
fi

cat <<EOF

>> ready
   kubeconfig : ${KUBECONFIG_FILE}
   namespace  : ${K8S_NAMESPACE}
   S3         : ${S3_ENDPOINT_URL}  (bucket ${S3_BUCKET})
   core       : ${DH_ENDPOINT}       -> start with deploy/local/run-core.sh
EOF

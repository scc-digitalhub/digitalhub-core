# Local development environment

Run digitalhub-core **on the host** (IDE / `mvnw`) against a local **Kind** cluster, with **SeaweedFS** as S3.
Run pods created by core can reach both core and S3.

```
           host                                     kind cluster "dhcore"
 ┌───────────────────────────┐            ┌─────────────────────────────────────┐
 │ core :8080                │──kubeconfig──▶ API server                        │
 │   KUBECONFIG=.kubeconfig  │            │                                     │
 │                           │◀──────────────── run pods (ns digitalhub)        │
 │ :8333 ── extraPortMapping ────────────────▶ SeaweedFS (ns seaweedfs, :30833)│
 └───────────────────────────┘            └─────────────────────────────────────┘
```

Core and the pods both use the same hostname, `host.docker.internal`, for core (`:8080`) and S3 (`:8333`).
This matters because core passes its own URLs (`DHCORE_ENDPOINT`, `aws_endpoint_url`) to the pods.

- **On the host**, the name resolves to `127.0.0.1` through `/etc/hosts`.
- **In the cluster**, `up.sh` adds a CoreDNS `hosts` entry. It points the name at the host's IP as seen from the Kind node: the node's `host.docker.internal` entry (Docker Desktop / Podman), or else the IPv4 gateway of the `kind` network (native Docker on Linux).

## Prerequisites

- `docker` (Docker Desktop, Podman or native), `kind`, `kubectl`, Java 21
- A one-time host mapping:
  ```sh
  echo '127.0.0.1 host.docker.internal' | sudo tee -a /etc/hosts
  ```

## Usage

```sh
deploy/local/up.sh          # create the cluster, patch CoreDNS, deploy SeaweedFS, create the bucket (idempotent)
deploy/local/run-core.sh    # build if needed, then run core with deploy/local/core.env
deploy/local/down.sh        # delete the cluster (S3 data included)
```

To run core from the IDE, load `deploy/local/core.env` as the env file and set `KUBECONFIG` to the absolute path of `deploy/local/.kubeconfig`.

| What | Value |
| --- | --- |
| Core / console | http://host.docker.internal:8080 (basic auth `admin` / `admin`) |
| S3 endpoint | http://host.docker.internal:8333, bucket `datalake` |
| S3 credentials | `dhcore` / `dhcore-secret`. These are static and shared with the pods through the S3 credentials provider. |
| Runs namespace | `digitalhub` |
| kubectl | `kubectl --kubeconfig deploy/local/.kubeconfig ...` |

The cluster name and hostname can be overridden with `CLUSTER_NAME=... CORE_HOST=... deploy/local/up.sh`.
If you change the hostname, update `core.env` to match.

## Notes

- Core and S3 must be reachable on the host interface the pods route to. Core listens on all interfaces by default, and the Kind port mapping binds `0.0.0.0:8333`. On Linux, a host firewall may need to allow traffic from the `kind` network.
- The database is the embedded H2 (`./data/db`).
- Metrics are disabled (`K8S_ENABLE_METRICS=false`) because there is no metrics-server.

### Optional additions (not included)

| Need | Add |
| --- | --- |
| Run metrics | Install metrics-server (with `--kubelet-insecure-tls` on Kind) and set `K8S_ENABLE_METRICS=true`. |
| Logs / metrics stack | Loki / Prometheus via their Helm charts. |
| Image builds (kaniko, container build) | A local registry container on the `kind` network ([kind local registry guide](https://kind.sigs.k8s.io/docs/user/local-registry/)), then set `IMAGE_REGISTRY`. |
| KFP / Ray / Argo / KubeAI / Flower runtimes | Install the corresponding operator into the cluster. |
| External OIDC | Set the `DH_AUTH_OIDC_*` variables (see `CONFIGURATION.md`). |
| PostgreSQL | Set `JDBC_PLATFORM`, `JDBC_URL`, `JDBC_DRIVER`, `JDBC_USER` and `JDBC_PASS`. |

#!/bin/sh

set -euo pipefail

if [ "$#" -ne 1 ] || [ -z "$1" ]; then
    echo "Usage: $0 ID" >&2
    exit 1
fi

: "${PROJECT_NAME:?PROJECT_NAME is required}"
: "${RUN_ID:?RUN_ID is required}"

RESOURCE=containerimage
ID=$1

export DHCLI_INSTALL_DIR="${DHCLI_INSTALL_DIR:-/shared/.local/bin}"
export PATH="${PATH:-/usr/bin:/bin}:$DHCLI_INSTALL_DIR"

if ! command -v dhcli >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
    DEPS_DIR=$(mktemp -d /shared/trivy-deps.XXXXXX)
    trap 'rm -f "$DEPS_DIR/install.sh" "$DEPS_DIR/jq"; rmdir "$DEPS_DIR"' EXIT
fi

if ! command -v dhcli >/dev/null 2>&1; then
    wget -O "$DEPS_DIR/install.sh" https://raw.githubusercontent.com/scc-digitalhub/digitalhub-cli/main/install.sh
    sh "$DEPS_DIR/install.sh" 0.16.1-beta2
fi

if ! command -v jq >/dev/null 2>&1; then
    if [ "$(uname -s)" != Linux ]; then
        echo "jq is required; automatic installation is only supported on Linux" >&2
        exit 1
    fi
    case "$(uname -m)" in
        x86_64|amd64)
            JQ_ARCH=amd64
            JQ_SHA256=020468de7539ce70ef1bceaf7cde2e8c4f2ca6c3afb84642aabc5c97d9fc2a0d
            ;;
        aarch64|arm64)
            JQ_ARCH=arm64
            JQ_SHA256=6bc62f25981328edd3cfcfe6fe51b073f2d7e7710d7ef7fcdac28d4e384fc3d4
            ;;
        *)
            echo "jq is required; unsupported architecture: $(uname -m)" >&2
            exit 1
            ;;
    esac
    wget -O "$DEPS_DIR/jq" "https://github.com/jqlang/jq/releases/download/jq-1.8.1/jq-linux-$JQ_ARCH"
    printf '%s  %s\n' "$JQ_SHA256" "$DEPS_DIR/jq" | sha256sum -c -
    mkdir -p "$DHCLI_INSTALL_DIR"
    chmod 755 "$DEPS_DIR/jq"
    mv "$DEPS_DIR/jq" "$DHCLI_INSTALL_DIR/jq"
fi

if ! IMAGE=$(dhcli get -p "$PROJECT_NAME" "$RESOURCE" "$ID" -o json |
    jq -er '.spec.image | select(type == "string" and length > 0)'); then
    echo "Failed to retrieve a container image for $RESOURCE $ID" >&2
    exit 1
fi

trivy image --format spdx-json --output report.json -- "$IMAGE"

SUMMARY=$(jq -ce '
    if type != "object" or (.spdxVersion | type) != "string"
       or (.spdxVersion | startswith("SPDX-2.") | not)
       or ((.packages != null) and (.packages | type) != "array") then
        error("invalid Trivy SPDX report")
    else
        [
            (.packages // [])[] |
            if type != "object" or ((.externalRefs != null) and (.externalRefs | type) != "array") then
                error("invalid Trivy SPDX package")
            else
                (.externalRefs // [])[] |
                if type != "object" or (.referenceCategory | type) != "string"
                   or (.referenceType | type) != "string" then
                    error("invalid Trivy SPDX external reference")
                else
                    select((.referenceCategory | ascii_upcase) == "SECURITY"
                        and (.referenceType | ascii_downcase) == "advisory")
                end
            end
        ] | length as $count |
        if $count > 0 then
            {assessment: "WARNING",
             findings: {CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, UNKNOWN: $count}}
        else {} end
    end
' report.json)

EXTENSION_ID=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')

jq -nr --arg id "$EXTENSION_ID" --arg name "security-report-$ID" --arg image "$IMAGE" --arg run "$RUN_ID" \
    --argjson summary "$SUMMARY" '
    "id: \($id | @json)",
    "kind: security-report",
    "name: \($name | @json)",
    "spec:",
    "  analyzer: Trivy Container Image SBOM",
    "  format: SPDX",
    "  image: \($image | @json)",
    "  run: \($run | @json)",
    if $summary | has("findings") then
        "  assessment: \($summary.assessment)",
        "  findings: \($summary.findings | tojson)"
    else empty end
' > security-report.yaml

dhcli extension create -p "$PROJECT_NAME" -f security-report.yaml "$RESOURCE" "$ID"
dhcli upload -p "$PROJECT_NAME" -f report.json extension "$EXTENSION_ID"
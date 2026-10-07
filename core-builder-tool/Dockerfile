ARG DHCLI_VERSION=0.15.0-beta3

# --------------------------
# dhcli for 32-bit ARM (arm/v7): the dhcli releases ship only amd64 and arm64, so it is
# built from the sources of the same release. dhcli is pure Go, so it is cross-compiled on
# the build machine without emulation. The other architectures skip this stage's output.
# --------------------------
FROM --platform=$BUILDPLATFORM golang:1.24-alpine AS dhcli-arm
ARG DHCLI_VERSION
ARG TARGETARCH
ARG TARGETVARIANT
RUN mkdir -p /out && if [ "$TARGETARCH" = "arm" ]; then \
        apk add --no-cache git && \
        git clone --depth 1 --branch "${DHCLI_VERSION}" https://github.com/scc-digitalhub/digitalhub-cli.git /src && \
        cd /src && \
        CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM="${TARGETVARIANT#v}" go build -trimpath -ldflags "-s -w" -o /out/dhcli . ; \
    fi

# --------------------------
# Minimal Alpine image for builder-tool.sh
# --------------------------
FROM alpine:3.18

ARG USER=builder
ARG USER_ID=1000
ARG USER_GROUP=builder
ARG USER_GROUP_ID=1000
ARG DRAGONFLY_VERSION=1.3.12
ARG DHCLI_VERSION
ARG TARGETARCH
# --------------------------
# Install essential dependencies + AWS CLI from Alpine repos
# --------------------------
# Alpine has no aws-cli package for 32-bit ARM: on arm/v7 AWS CLI v1 is installed with pip,
# which offers the same `aws s3 cp` used by builder-tool.sh.
RUN apk add --no-cache \
        rsync \
        git \
        curl \
        bash \
        unzip \
        tar \
        gzip \
        python3 \
        py3-pip \
    && if [ "$TARGETARCH" = "arm" ]; then \
        pip3 install --no-cache-dir --break-system-packages awscli; \
    else \
        apk add --no-cache aws-cli; \
    fi \
    && aws --version
RUN pip3 install --no-cache-dir --break-system-packages "huggingface_hub[cli]" \
    && hf --help > /dev/null

# --------------------------
# Install digitalhub CLI (dhcli): the release binary, or the one built above for arm/v7
# --------------------------
COPY --from=dhcli-arm /out/ /tmp/dhcli-arm/
RUN if [ "$TARGETARCH" = "arm" ]; then \
        mv /tmp/dhcli-arm/dhcli /usr/bin/dhcli && chmod +x /usr/bin/dhcli && rm -rf /tmp/dhcli-arm && dhcli --help > /dev/null && exit 0; \
    fi && \
    rm -rf /tmp/dhcli-arm && \
    case "$TARGETARCH" in \
        amd64) DHCLI_ARCH="amd64" ;; \
        arm64) DHCLI_ARCH="arm64" ;; \
        *) echo "Unsupported architecture: $TARGETARCH" && exit 1 ;; \
    esac && \
    mkdir -p /tmp/dhcli && \
    wget -O /tmp/dhcli/dhcli.tar.gz \
        "https://github.com/scc-digitalhub/digitalhub-cli/releases/download/${DHCLI_VERSION}/dhcli-linux-${DHCLI_ARCH}.tar.gz" && \
    tar -xzf /tmp/dhcli/dhcli.tar.gz -C /tmp/dhcli && \
    find /tmp/dhcli -name 'dhcli' -type f -exec mv {} /usr/bin/dhcli \; && \
    chmod +x /usr/bin/dhcli && \
    rm -rf /tmp/dhcli

# --------------------------
# Install dragonfly binary. There is no 32-bit ARM release: on arm/v7 dfget is left out
# and builder-tool.sh downloads without it, as it does whenever dfget is missing.
# --------------------------
RUN case "$TARGETARCH" in \
        amd64) DRAGONFLY_ARCH="x86_64" ;; \
        arm64) DRAGONFLY_ARCH="aarch64" ;; \
        arm) echo "dragonfly: no 32-bit ARM release, dfget not installed" && exit 0 ;; \
        *) echo "Unsupported architecture: $TARGETARCH" && exit 1 ;; \
    esac && \
    mkdir -p /tmp/dragonfly && \
    wget -O /tmp/dragonfly/client.tar.gz \
        "https://github.com/dragonflyoss/client/releases/download/v${DRAGONFLY_VERSION}/dragonfly-client-v${DRAGONFLY_VERSION}-${DRAGONFLY_ARCH}-unknown-linux-musl.tar.gz" && \
    tar -xzf /tmp/dragonfly/client.tar.gz --strip-components=1 -C /tmp/dragonfly && \
    mv /tmp/dragonfly/dfget /usr/bin/dfget && \
    rm -rf /tmp/dragonfly

# --------------------------
# Create non-root user
# --------------------------
RUN addgroup -g ${USER_GROUP_ID} ${USER_GROUP} && \
    adduser -D -u ${USER_ID} -G ${USER_GROUP} ${USER}

# --------------------------
# Set working directory
# --------------------------
WORKDIR /app

# --------------------------
# Copy builder script and make it world-executable
# --------------------------
COPY --chown=${USER_GROUP_ID}:${USER_GROUP_ID} builder-tool.sh /app/builder-tool.sh

# Make it executable by any user (for runAsUser with arbitrary UIDs)
RUN chmod 555 /app/builder-tool.sh && \
    chmod 755 /app

# --------------------------
# Run as non-root user
# --------------------------
USER ${USER_GROUP_ID}

# --------------------------
# Set entrypoint to invoke builder-tool.sh
# --------------------------
ENTRYPOINT ["/app/builder-tool.sh"]

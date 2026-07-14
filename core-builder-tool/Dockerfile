# --------------------------
# Minimal Alpine image for builder-tool.sh
# --------------------------
FROM alpine:3.18

ARG USER=builder
ARG USER_ID=1000
ARG USER_GROUP=builder
ARG USER_GROUP_ID=1000
ARG DRAGONFLY_VERSION=1.3.12
ARG DHCLI_VERSION=0.15.0-beta3
# --------------------------
# Install essential dependencies + AWS CLI from Alpine repos
# --------------------------
RUN apk add --no-cache \
        rsync \
        git \
        curl \
        bash \
        unzip \
        tar \
        gzip \
        aws-cli \
        python3 \
        py3-pip \
    && aws --version
RUN pip3 install --no-cache-dir --break-system-packages "huggingface_hub[cli]" \
    && hf --help > /dev/null

# --------------------------
# Install digitalhub CLI (dhcli)
# --------------------------
RUN ARCH=$(uname -m) && \
    case "$ARCH" in \
        x86_64)  DHCLI_ARCH="amd64" ;; \
        aarch64) DHCLI_ARCH="arm64" ;; \
        *) echo "Unsupported architecture: $ARCH" && exit 1 ;; \
    esac && \
    mkdir -p /tmp/dhcli && \
    wget -O /tmp/dhcli/dhcli.tar.gz \
        "https://github.com/scc-digitalhub/digitalhub-cli/releases/download/${DHCLI_VERSION}/dhcli-linux-${DHCLI_ARCH}.tar.gz" && \
    tar -xzf /tmp/dhcli/dhcli.tar.gz -C /tmp/dhcli && \
    find /tmp/dhcli -name 'dhcli' -type f -exec mv {} /usr/bin/dhcli \; && \
    chmod +x /usr/bin/dhcli && \
    rm -rf /tmp/dhcli

# --------------------------
# Install dragonfly binary
# --------------------------
RUN mkdir -p /tmp/dragonfly && \
    wget -O /tmp/dragonfly/client.tar.gz \
        https://github.com/dragonflyoss/client/releases/download/v${DRAGONFLY_VERSION}/dragonfly-client-v${DRAGONFLY_VERSION}-x86_64-unknown-linux-musl.tar.gz && \
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

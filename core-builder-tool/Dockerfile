# --------------------------
# Minimal Alpine image for builder-tool.sh
# --------------------------
FROM alpine:3.18

ARG USER=builder
ARG USER_ID=1000
ARG USER_GROUP=builder
ARG USER_GROUP_ID=1000

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
    && aws --version

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

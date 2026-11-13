#!/bin/sh

if [ "$#" -lt 1 ] || [ -z "$1" ] || { [ "$#" -ge 2 ] && [ -z "$2" ]; }; then
    echo "Usage: $0 MODEL [ALIAS]" >&2
    exit 2
fi

model=$1

ollama serve &
SERVER_PID=$!

ollama_client() {
    OLLAMA_HOST=127.0.0.1:11434 NO_PROXY=127.0.0.1,localhost no_proxy=127.0.0.1,localhost ollama "$@"
}

cleanup() {
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
}

trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT

echo "Waiting for Ollama server..."
until ollama_client list >/dev/null; do
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        wait "$SERVER_PID"
        exit 1
    fi
    echo "Ollama server is not ready; retrying in 2 seconds..."
    sleep 2
done

echo "Pulling model: ${model}"
if ! ollama_client pull "$model"; then
    echo "Failed to pull model: ${model}" >&2
    exit 1
fi

if [ "$#" -ge 2 ]; then
    echo "Creating model alias: $2"
    if ! ollama_client cp "$model" "$2"; then
        echo "Failed to create model alias: $2" >&2
        exit 1
    fi
fi

echo "Model ready."
wait "$SERVER_PID"
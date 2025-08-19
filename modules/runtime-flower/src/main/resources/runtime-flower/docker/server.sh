#!/bin/bash
die() {
    printf "Init failed: %s\n\n" "$1"
    exit 1
}

usage() {
    echo ""
    echo "Run server."
    echo ""
    echo "usage: server.sh path_to_project [other_args]"
    echo ""
    echo "path_to_project path to flower project"
    echo ""
}

PYTHON_BIN=$(which python3)
path_to_project="."

# Parse parameters
if [ $# -gt 0 ]; then
    if [[ $1 == "--help" ]]; then
        usage
        exit 0
    else 
        path_to_project="$1"
        shift
    fi
fi

# check path_to_project
if [[ -z $path_to_project ]]; then
    usage
    die "Missing parameter path_to_project"
fi

cd "${path_to_project}"

echo "Installing requirements from pyproject.toml at ${path_to_project}..."
${PYTHON_BIN} -m pip install -U --no-cache-dir .
if ! [ $? -eq 0 ]; then
    die "Error installing requirements from pyproject.toml"
fi

if [ -f certificates/ca.crt ] && [ -f certificates/tls.conf ]; then
    echo "generate server keys..."
    openssl genrsa -out certificates/server.key 4096
    openssl req -new  -key certificates/server.key  -out certificates/server.csr  -config certificates/tls.conf
    openssl x509  -req  -in certificates/server.csr  -CA certificates/ca.crt  -CAkey certificates/ca.key  -CAcreateserial  -out certificates/server.pem  -days 365  -sha256 -extfile certificates/tls.conf -extensions req_ext 
fi

export HOME="${path_to_project}"
export FLOWER_HOME="${path_to_project}"

# run processors
CMD="flower-superlink"

echo "Launch ${CMD}..."
$CMD "$@"
if ! [ $? -eq 0 ]; then
    die "Error executing processor"
fi

exit

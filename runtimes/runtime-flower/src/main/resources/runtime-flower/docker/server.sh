#!/bin/bash
die() {
    printf "Server app failed: %s\n\n" "$1"
    exit 1
}

usage() {
    echo ""
    echo "Run server."
    echo ""
    echo "usage: server.sh --path_to_project path_to_project --certificate path_to_certificate --tls_conf path_to_tls_conf --keys path_to_keys"
    echo ""
    echo "  --path path_to_project    path to flower project"
    echo "  --certificate path_to_certificate  path to certificate file"
    echo "  --tls_conf path_to_tls_conf  path to tls configuration file"
    echo "  --keys path_to_keys       path to keys csv file"
    echo ""
}

echo "Starting init script with args $@..."

PYTHON_BIN=$(which python3)
path_to_project="."
cert_args="--insecure"
auth_args=""

# Parse parameters
while [ $# -gt 0 ]; do
    if [[ $1 == "--help" ]]; then
        usage
        exit 0
    elif [[ $1 == "--"* ]]; then
        v="${1/--/}"
        declare "$v"="$2"
        shift
    fi
    shift
done

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

if [ -n "${certificate}" ] && [ -n "${tls_conf}" ]; then
    echo "generate server keys..."
    openssl genrsa -out certificates/server.key 4096
    openssl req -new  -key certificates/server.key  -out certificates/server.csr  -config certificates/tls.conf
    openssl x509  -req  -in certificates/server.csr  -CA certificates/ca.crt  -CAkey certificates/ca.key  -CAcreateserial  -out certificates/server.pem  -days 365  -sha256 -extfile certificates/tls.conf -extensions req_ext 
    cert_args="--ssl-ca-certfile ${certificate} --ssl-certfile certificates/server.pem --ssl-keyfile certificates/server.key"
fi

if [ -n "${keys}" ]; then
    auth_args="--auth-list-public-keys ${keys}"
fi

export HOME="${path_to_project}"
export FLOWER_HOME="${path_to_project}"

# run processors
CMD="flower-superlink"

echo "Launch ${CMD} ${cert_args} ${auth_args}..."
$CMD ${cert_args} ${auth_args}
if ! [ $? -eq 0 ]; then
    die "Error executing processor"
fi

exit

#!/bin/bash
die() {
    printf "Client app failed: %s\n\n" "$1"
    exit 1
}

usage() {
    echo ""
    echo "Run client."
    echo ""
    echo "usage: server.sh --path_to_project path_to_project --certificate path_to_certificate --superlink superlink_url --public_key public_key_value --private_key private_key_value --node_config node_config --isolation isolation_type"
    echo ""
    echo "  --path path_to_project    path to flower project"
    echo "  --certificate path_to_certificate  path to certificate file"
    echo "  --superlink superlink_url   superlink url"
    echo "  --public_key public_key_value  public key value for supernode authentication"
    echo "  --private_key private_key_value  private key value for supernode authentication"
    echo "  --node_config node_config  node config parameters spec"
    echo "  --isolation isolation_type  isolation type for the client (default: subprocess)"
    echo ""
}

PYTHON_BIN=$(which python3)
path_to_project="."
cert_args="--insecure"
auth_args=""
isolation_args="--isolation subprocess"

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
# check superlink
if [[ -z $superlink ]]; then
    usage
    die "Missing parameter superlink"
fi

cd "${path_to_project}"

echo "Installing requirements from pyproject.toml at ${path_to_project}..."
${PYTHON_BIN} -m pip install -U --no-cache-dir .
if ! [ $? -eq 0 ]; then
    die "Error installing requirements from pyproject.toml"
fi

if [ -n "${certificate}" ]; then
    cert_args="--root-certificates ${certificate}"
fi

export HOME="${path_to_project}"
export FLOWER_HOME="${path_to_project}"

if [ ! -z $public_key ] && [ ! -z $private_key ]; then
    auth_args="--auth-supernode-private-key $private_key --auth-supernode-public-key $public_key"
fi    



# run processors
CMD="flower-supernode"

echo "Launch ${CMD}  ${cert_args} ${auth_args} ${isolation_args} --superlink ${superlink} --node-config '${node_config}'..."
$CMD ${cert_args} ${auth_args} ${isolation_args} --superlink ${superlink} --node-config "${node_config}"
if ! [ $? -eq 0 ]; then
    die "Error executing processor"
fi

exit

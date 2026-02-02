#!/bin/bash
die() {
    printf "Init failed: %s\n\n" "$1"
    exit 1
}

usage() {
    echo ""
    echo "Run processor with additional env initialization."
    echo ""
    echo "usage: entrypoint.sh --processor path_to_processor --config path_to_yaml --requirements path_to_req.txt --common_requirements path_to_common_req.txt --platform_config path_to_yaml --uv_path uv_path --wheel_path wheel_path"
    echo ""
    echo "  --processor string                  path to processor binary"
    echo "  --config string                     path to function yaml"
    echo "  --requirements string               path to (optional) requirements.txt"
    echo "  --common_requirements string        path to (optional) common requirements.txt"
    echo "  --platform_config string            path to (override) platform config yaml"
    echo "  --uv_path string                    path to uv binary"
    echo "  --wheel_path string                 path to wheel directory"
    echo ""
}

config="/etc/nuclio/config/processor/processor.yaml"

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

# read from python installed version
PYTHON_VERSION=$((python3 --version) 2>&1)
if [[ $? -ne 0 ]]; then
    die "Python3 not found"
fi
PYTHON_VERSION=$(echo ${PYTHON_VERSION} | awk '{print $2}')
echo "Using Python version: ${PYTHON_VERSION}"

INSTALL_CMD="$(which python3) -m pip install --user"
if [ -f "${uv_path}" ]; then
    ${uv_path} venv /shared/.venv  --system-site-packages --allow-existing --cache-dir /shared/.uv_cache
    source /shared/.venv/bin/activate
    INSTALL_CMD="${uv_path} pip install --cache-dir /shared/.uv_cache"
fi
echo "Using install command: ${INSTALL_CMD}"

WHEEL_OPTIONS=""
if [ -d "${wheel_path}" ]; then
    WHEEL_OPTIONS="--find-links ${wheel_path}"
fi

# check config
if [[ -z $processor ]]; then
    # try fallback
    if [[ -f "/usr/local/bin/processor" ]]; then
        processor="/usr/local/bin/processor"
    else
        usage
        die "Missing parameter --processor"
    fi
fi

if [[ -z $config ]]; then
    usage
    die "Missing parameter --config"
fi

echo "Initializing for processor ${processor} with config ${config} (${platform_config})..."

if ! [ -f "${processor}" ]; then
    die "Invalid or missing processor specified"
fi

if ! [ -f "${config}" ]; then
    die "Invalid or missing config file specified"
fi

# common requirements
if  [ -f "${common_requirements}" ]; then
    ${INSTALL_CMD}  --no-index ${WHEEL_OPTIONS} -r "${common_requirements}"
    if ! [ $? -eq 0 ]; then
        die "Error installing common requirements"
    fi
fi

# if requirements are defined try to install
if [[ -n "${requirements}" ]]; then
    if  [[ -f "${requirements}" ]]; then
        echo "Installing requirements from ${requirements}..."
        
        ${INSTALL_CMD} ${WHEEL_OPTIONS} -r "${requirements}"
        if ! [ $? -eq 0 ]; then
            die "Error installing requirements from ${requirements}"
        fi
    fi
fi

# run processors
echo "Run processor ${processor} with config ${config}..."
CMD="${processor} --config ${config}"

if [ -n $platform_config ] && [ -f "${platform_config}" ]; then
    CMD="${CMD} --platform-config ${platform_config}"
fi

echo "Launch ${CMD}..."

$CMD
if ! [ $? -eq 0 ]; then
    die "Error executing processor"
fi

exit

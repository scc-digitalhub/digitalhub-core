#!/bin/bash
die() {
    printf "Init failed: %s\n\n" "$1"
    exit 1
}

usage() {
    echo ""
    echo "Run processor with additional env initialization."
    echo ""
    echo "usage: entrypoint.sh --config path_to_yaml --requirements path_to_req.txt --platform_config path_to_yaml"
    echo ""
    echo "  --config string           path to function yaml"
    echo "  --requirements string     path to (optional) requirements.txt"
    echo "  --platform_config string  path to (override) platform config yaml"
    echo ""
}

PYTHON_BIN=$(which python3)
processor="/usr/local/bin/processor"
config="/etc/nuclio/config/processor/processor.yaml"
platform_config="/etc/nuclio/config/platform/platform.yaml"

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

# check config
if [[ -z $processor ]]; then
    usage
    die "Missing parameter --processor"
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

# if requirements are defined try to install
if [[ -n "${requirements}" ]]; then
    if  [[ -f "${requirements}" ]]; then
        echo "Installing requirements from ${requirements}..."
        ${PYTHON_BIN} -m pip install --user -r "${requirements}"
        if ! [ $? -eq 0 ]; then
            die "Error installing requirements from ${requirements}"
        fi
    fi
fi

# run processors
echo "Run processor ${processor} with config ${config} (${platform_config})..."
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

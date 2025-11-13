#!/bin/bash

die() {
    printf "App run failed: %s\n\n" "$1"
    exit 1
}

usage() {
    echo ""
    echo "Run client."
    echo ""
    echo "usage: app.sh path_to_project flwr_args"
    echo ""
    echo "  path_to_project  path to flower project"
    echo ""
}
PYTHON_BIN=$(which python3)

path_to_project="."
if [[ $1 == "--help" ]]; then
    usage
    exit 0
else
    path_to_project=$1
    shift
fi

cd "${path_to_project}"

# echo "Installing digitalhub..."
# ${PYTHON_BIN} -m pip install digitalhub
# if ! [ $? -eq 0 ]; then
#     die "Error installing digitalhub"
# fi

# prepare python script to process flwe output
echo 'import json, sys, os, requests
res = json.load(open("status.json")) 
if not res["success"]: sys.exit(res["error-message"])
with open("flwr_run_id", "w") as f:
    f.write(str(res["run-id"]))

url = f"{os.environ["DHCORE_ENDPOINT"]}/api/v1/-/{os.environ.get("PROJECT_NAME")}/runs/{os.environ.get("RUN_ID")}"
headers = {"Authorization": f"Bearer {os.environ.get("DHCORE_ACCESS_TOKEN")}"}
try:
    run = requests.get(url, headers=headers).json()
    run["status"]["flwr_run_id"] = str(res["run-id"])
    # workaround for validation bug
    run["name"] = run["id"]
    run = requests.put(url, json=run, headers=headers).json()
except Error as exc:
    sys.exit("error updating run status: {exc}")' > process_output.py

# dh.get_run(os.environ.get("RUN_ID"), project=os.environ.get("PROJECT_NAME"))
# run['status']['flwr_run_id'] = str(res["run-id"])
# dh.update_run(run)' > process_output.py

echo "Running flower app with arguments: $@"
flwr "$@" > status.json

# Parse the success field from output.json
python3 process_output.py
if ! [ $? -eq 0 ]; then
    die "Error running flower app"
fi

FLWR_RUN_ID=$(cat flwr_run_id)
echo "Flower app run is running: ${FLWR_RUN_ID}."

if [ ! -z "$FLWR_RUN_ID" ]; then
    flwr log "$FLWR_RUN_ID"
fi
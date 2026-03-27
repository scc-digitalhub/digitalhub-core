#!/bin/bash
set -euo pipefail

# --------------------------
# Default configuration from ENV
# --------------------------
destination_dir="${DESTINATION_DIR:-/shared}"
temp_dir="${TEMP_DIR:-}"
parallelism="${PARALLELISM:-5}"  # Number of parallel jobs
DEBUG="${DEBUG:-false}"          # If true, print detailed listing at the end
CONTEXT_REFS="${CONTEXT_REFS:-/init-config-map/context-refs.txt}"
CONTEXT_SOURCES="${CONTEXT_SOURCES:-/init-config-map/context-sources-map.txt}"

# --------------------------
# Parse named arguments
# --------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --context-refs)
            CONTEXT_REFS="$2"
            shift 2
            ;;
        --context-sources-map)
            CONTEXT_SOURCES="$2"
            shift 2
            ;;
        --destination-dir)
            destination_dir="$2"
            shift 2
            ;;
        --temp-dir)
            temp_dir="$2"
            shift 2
            ;;
        --debug)
            DEBUG="true"
            shift
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# --------------------------
# Validate destination directory
# --------------------------
if [ -z "$destination_dir" ]; then
    echo "Error: --destination-dir is required"
    exit 1
fi
if [ -z "$temp_dir" ]; then
    temp_dir="${TEMP_DIR:-$destination_dir/.tmp}"    
fi
echo "Destination dir: $destination_dir"
echo "Temporary dir: $temp_dir"
echo "Context refs file: ${CONTEXT_REFS:-<skipped>}"
echo "Context sources map: ${CONTEXT_SOURCES:-<skipped>}"
echo "DEBUG mode: $DEBUG"

mkdir -p "$destination_dir"
mkdir -p "$temp_dir"


# --------------------------
# Configure tools for read-only filesystem
# --------------------------
# Prevent AWS CLI from writing to ~/.aws/ or caching credentials
export AWS_CONFIG_FILE="${destination_dir}/.aws-config"
export AWS_SHARED_CREDENTIALS_FILE="${destination_dir}/.aws-credentials"
export AWS_CA_BUNDLE="${AWS_CA_BUNDLE:-}"  # Allow custom CA if needed
# Disable credential process caching that tries to write to ~/.aws/cli/cache/
export AWS_METADATA_SERVICE_TIMEOUT=1
export AWS_METADATA_SERVICE_NUM_ATTEMPTS=1

# Prevent Git from writing to ~/.gitconfig or reading system config
export GIT_CONFIG_NOSYSTEM=1
export HOME="${destination_dir}"
# Disable Git's safe.directory check (can cause issues in read-only environments)
export GIT_CONFIG_COUNT=1
export GIT_CONFIG_KEY_0="safe.directory"
export GIT_CONFIG_VALUE_0="*"

# Curl: disable .netrc and cookie jar writes (not needed for our use case)
export CURL_HOME="${destination_dir}"

# --------------------------
# Helper Functions
# --------------------------
decode_base64_no_padding() {
    local base64_filename="$1"
    while [ $((${#base64_filename} % 4)) -ne 0 ]; do
        base64_filename="${base64_filename}="
    done
    echo "$base64_filename" | base64 -d
}

s3_cp() {
    local src="$1"
    local dest="$2"
    local s3_dest="$dest"
    if [ -d "$dest" ]; then
        s3_dest="$dest/$(basename "$src")"   
    fi
    echo "Downloading file $src -> $s3_dest"
    if [ -n "${AWS_ENDPOINT_URL:-}" ]; then
        aws s3 cp "$src" "$s3_dest" --endpoint-url "$AWS_ENDPOINT_URL"
    else
        aws s3 cp "$src" "$s3_dest"
    fi
}

s3_cp_recursive() {
    local src="$1"
    local dest="$2"
    # recursive copy expects a folder path ending with /
    # we'll copy the content to dest, so we don't want the folder itself to be created under dest
    echo "Downloading folder $src -> $dest"
    if [ -n "${AWS_ENDPOINT_URL:-}" ]; then
        aws s3 cp "$src" "$dest" --recursive --endpoint-url "$AWS_ENDPOINT_URL"
    else
        aws s3 cp "$src" "$dest" --recursive
    fi
}

download_s3_zip() {
    local src="$1"
    local dest="$2"
    # Remove zip+s3:// and replace with s3://
    local s3_uri="${src/zip+s3:\/\//s3:\/\/}"
    local zip_dest="$temp_dir/$(basename "$s3_uri")"
    echo "Downloading ZIP $s3_uri -> $zip_dest"
    s3_cp "$s3_uri" "$zip_dest"
    
    # Check if dest is a folder
    if [ -d "$dest" ]; then
        # Unzip INTO the existing directory
        unzip -o "$zip_dest" -d "$dest"
    else
        # Create the destination as a directory and unzip into it
        mkdir -p "$dest"
        unzip -o "$zip_dest" -d "$dest"
    fi
}

handle_error() {
    local lineno=$1
    local message=$2
    echo "Error: $message at line $lineno"
    exit 1
}
trap 'handle_error $LINENO "$BASH_COMMAND"' ERR

# --------------------------
# Sync files from context-sources-map
# --------------------------
sync_files() {
    local map_file="$1"
    local base64_dir="$2"
    local destination_dir="$3"
    [ ! -f "$map_file" ] && echo "Error: $map_file missing" && exit 1
    while IFS=',' read -r base64_filename destination_path; do
        base64_filename=$(echo "$base64_filename" | xargs)
        destination_path=$(echo "$destination_path" | xargs)
        source_file="$base64_dir/$base64_filename"
        [ ! -f "$source_file" ] && echo "Warning: $source_file missing, skipping" && continue
        dest_path="$destination_dir/$destination_path"
        mkdir -p "$(dirname "$dest_path")"
        cp "$source_file" "$dest_path" && echo "Copied $source_file -> $dest_path"
    done < "$map_file"
}

# --------------------------
# Process a single context-ref line with progress
# --------------------------
process_context_ref() {
    local protocol="$1"
    local destination="$2"
    local source="$3"
    local counter_file="$4"
    set -e

    protocol=$(echo "$protocol" | xargs)
    destination=$(echo "$destination" | xargs)
    source=$(echo "$source" | xargs)

    # by default write to base destination
    dest="$destination_dir"
    if [ -n "$destination" ]; then
        # Remove leading "/" from destination to avoid double slashes
        destination="${destination#/}"
        
        # Security check: reject paths with ".." to prevent escaping base folder
        if [[ "$destination" == *"../"* ]] || [[ "$destination" == *"/.."* ]]; then
            echo "Error: destination path contains '..' which could escape base folder: $destination"
            exit 1
        fi
        
        dest="$destination_dir/$destination"            
        mkdir -p "$(dirname "$dest")"
    fi

    if [ "$DEBUG" = "true" ]; then
        echo "DEBUG: Processing line - protocol: $protocol, destination: $dest, source: $source"
    fi

    case "$protocol" in
        "git+https")
            # Remove git+https and replace with https
            git_uri="${source/git+https:\/\//https:\/\/}"
            branch="${git_uri##*#}"
            repo="${git_uri%#*}"
            [ "$repo" == "$git_uri" ] && branch=""
            # Strip https:// from repo
            repo="${repo#https://}"
            
            if [ -n "${GIT_TOKEN:-}" ]; then
                if [[ $GIT_TOKEN == github_pat_* || $GIT_TOKEN == glpat* ]]; then
                    username="oauth2"; password="$GIT_TOKEN"
                else
                    username="$GIT_TOKEN"; password="x-oauth-basic"
                fi
            elif [ -n "${GIT_USERNAME:-}" ] && [ -n "${GIT_PASSWORD:-}" ]; then
                username="$GIT_USERNAME"; password="$GIT_PASSWORD"
            else
                username=""; password=""
            fi
            
            # Build clone URL with credentials if provided
            if [ -n "$username" ]; then
                clone_url="https://$username:$password@$repo"
            else
                clone_url="https://$repo"
            fi

            gv=""
            temp_suffix=$(echo -n "$git_uri" | base64 -w 0 | tr -d '=' | tr '/+' '_-' | cut -c1-32)
            temp_clone_dir="$temp_dir/_temp_$temp_suffix"
            rm -rf "$temp_clone_dir"; mkdir -p "$temp_clone_dir"

            if [ "$DEBUG" = "true" ]; then
                gv=" -v "
                echo "DEBUG: Cloning git repository from $clone_url to $temp_clone_dir"
                [ -n "$branch" ] && echo "DEBUG: Clone branch $branch"
            fi

            # Clone with minimal config to avoid writing to ~/.gitconfig
            if [ -n "$branch" ]; then
                git clone $gv --branch "$branch" --config core.askpass='' --recurse-submodules "$clone_url" "$temp_clone_dir"
            else
                git clone $gv --config core.askpass='' --recurse-submodules "$clone_url" "$temp_clone_dir"
            fi
            
            cp -a "$temp_clone_dir/." "$dest/"
            rm -rf "$temp_clone_dir"
        ;;
        "zip+s3")            
            download_s3_zip "$source" "$dest"
        ;;
        "s3")
            # Check if source ends with / (folder) or not (file)
            if [[ "$source" == */ ]]; then
                s3_cp_recursive "$source" "$dest"
            else
                s3_cp "$source" "$dest"
            fi
        ;;
        "zip+http"|"zip+https")
            stripped_source="${source#zip+}"
            local http_dest="$(basename "$stripped_source")"
            
            # Download zip to temp directory
            curl -L -f -o "$temp_dir/$http_dest" "$stripped_source"
            
            # Check if dest is a folder
            if [ -d "$dest" ]; then
                # Unzip INTO the existing directory
                unzip -o "$temp_dir/$http_dest" -d "$dest"
            else
                # Create the destination as a directory and unzip into it
                mkdir -p "$dest"
                unzip -o "$temp_dir/$http_dest" -d "$dest"
            fi
        ;;
        "http"|"https")
            # single source will keep filename when dest is a dir
            local http_dest="$(basename "$source")"
            
            # Check if dest is a folder
            if [ -d "$dest" ]; then
                # Write to $dest/$http_dest if dest is a directory
                curl -L -f -o "$dest/$http_dest" "$source"
            else
                # Write directly to $dest if it's not a directory
                mkdir -p "$(dirname "$dest")"
                curl -L -f -o "$dest" "$source"
            fi
        ;;
        *)
            echo "Unknown protocol: $protocol"
            exit 1
        ;;
    esac

    # --------------------------
    # Update progress counter
    # --------------------------
    flock "$counter_file" -c "echo \$((\$(cat $counter_file)+1)) > $counter_file"
    completed=$(cat "$counter_file")
    echo "Progress: $completed/$TOTAL_LINES completed"
}
export -f process_context_ref
export -f download_s3_zip
export -f s3_cp
export -f s3_cp_recursive

# --------------------------
# Parallel processing with progress
# --------------------------
if [ -n "$CONTEXT_REFS" ] && [ -f "$CONTEXT_REFS" ]; then
    cd "$destination_dir"
    TOTAL_LINES=$(wc -l < "$CONTEXT_REFS")
    counter_file="$destination_dir/_progress_counter"
    echo 0 > "$counter_file"
    export TOTAL_LINES counter_file

    # Process context refs in parallel
    pids=""
    while IFS=',' read -r protocol destination source || [ -n "$protocol" ]; do
        # Skip empty lines
        [ -z "$protocol" ] && continue
        
        # Trim whitespace
        protocol=$(echo "$protocol" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
        destination=$(echo "$destination" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
        source=$(echo "$source" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
        
        # Skip if any field is empty
        [ -z "$protocol" ] || [ -z "$source" ] && continue
        
        # Run in background with parallelism control
        while [ $(jobs -r | wc -l) -ge "$parallelism" ]; do
            sleep 0.1
        done
        
        process_context_ref "$protocol" "$destination" "$source" "$counter_file" &
        pids="$pids $!"
    done < "$CONTEXT_REFS"
    
    # Wait for all jobs to complete
    failed=0
    for pid in $pids; do
        wait $pid || failed=1
    done
    
    if [ $failed -eq 1 ]; then
        echo "Error: One or more downloads failed"
        exit 1
    fi

    rm -f "$counter_file"
fi

# --------------------------
# Process context-sources-map
# --------------------------
[ -n "$CONTEXT_SOURCES" ] && [ -f "$CONTEXT_SOURCES" ] && \
    sync_files "$CONTEXT_SOURCES" "$(dirname "$CONTEXT_SOURCES")" "$destination_dir"

# --------------------------
# Set permissions
# --------------------------
# Use glob pattern to handle filenames with spaces
shopt -s dotglob
for f in "$destination_dir"/*; do
    [ ! -e "$f" ] && continue
    [ "$(basename "$f")" == "lost+found" ] && continue
    chmod u=rwxX,g=rwxX -R "$f"
done

# --------------------------
# Final recap
# --------------------------
file_count=$(find "$destination_dir" -type f ! -path "$destination_dir/lost+found/*" | wc -l)
total_size=$(du -sh "$destination_dir" | awk '{print $1}')

echo "================ Recap ================="
echo "Total files downloaded/copied: $file_count"
echo "Total size: $total_size"
echo "======================================="

# Optional debug listing
if [ "$DEBUG" = "true" ]; then
    echo "Listing all files in $destination_dir:"
    ls -1l "$destination_dir"
fi

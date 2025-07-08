#!/bin/bash


# echo "Scheme: ${url_parts[0]}"
# echo "Netloc: ${url_parts[1]}"
# echo "Path: ${url_parts[2]}"
# echo "Query: ${url_parts[3]}"
# echo "Fragment: ${url_parts[4]}"
# Parse URL and return URL parts as an array
urlparse() {
    local url="$1"
    local scheme
    local netloc
    local path
    local query
    local fragment

    # Extract scheme
    scheme="${url%%://*}"
    url="${url#"$scheme://"}"

    # Extract netloc
    netloc="${url%%/*}"
    url="${url#"$netloc"}"

    # Extract path
    path="${url%%\?*}"
    url="${url#"$path"}"

    # Extract query
    query="${url%%\#*}"
    url="${url#"$query"}"

    # Extract fragment
    fragment="${url##\#}"

    # Store components in an array
    local components=("$scheme" "$netloc" "$path" "$query" "$fragment")

    # Return the array
    echo "${components[@]}"
}

# Rebuild URL from URL parts array
rebuild_url() {
    local url_parts=("$@")  # Accepts the url_parts array as arguments
    local url=""

    # Append scheme (url_parts[0]) if present
    # if [ -n "${url_parts[0]}" ]; then
    #     url="${url_parts[0]}://"
    # fi

    # Append netloc (url_parts[1]) if present
    if [ -n "${url_parts[1]}" ]; then
        url="${url}${url_parts[1]}"
    fi

    # Append path (url_parts[2]) if present
    if [ -n "${url_parts[2]}" ]; then
        url="${url}${url_parts[2]}"
    fi

    # Append query (url_parts[3]) if present
    if [ -n "${url_parts[3]}" ]; then
        url="${url}?${url_parts[3]}"
    fi

    # Append fragment (url_parts[4]) if present
    if [ -n "${url_parts[4]}" ]; then
        url="${url}#${url_parts[4]}"
    fi

    echo "$url"
}



# Exit immediately if any command fails
set -e

# Source directory with materialized files
source_dir="/init-config-map"

# Destination directory shared between containers
destination_dir="/shared"
tmp_dir="/tmp/shared"

# set minio alias for mc
if [[ ! -z "${AWS_SESSION_TOKEN}" && ! -z "${AWS_ENDPOINT_URL}"  ]]; then
    PARTS=($(urlparse $AWS_ENDPOINT_URL))
    S3_ENDPOINT="${PARTS[1]}"
    S3_SCHEME="${PARTS[0]}"
    MC_HOST="${S3_SCHEME}://${AWS_ACCESS_KEY_ID}:${AWS_SECRET_ACCESS_KEY}:${AWS_SESSION_TOKEN}@${S3_ENDPOINT}"
elif [[ -z "${AWS_SESSION_TOKEN}" && ! -z "${AWS_ENDPOINT_URL}"  ]]; then
    PARTS=($(urlparse $AWS_ENDPOINT_URL))
    S3_ENDPOINT="${PARTS[1]}"
    S3_SCHEME="${PARTS[0]}"
    MC_HOST="${S3_SCHEME}://${AWS_ACCESS_KEY_ID}:${AWS_SECRET_ACCESS_KEY}@${S3_ENDPOINT}"
else
    MC_HOST=""
fi

# Error handling function
handle_error() {
    local lineno=$1
    local message=$2
    echo "Error: $message at line $lineno"
    exit 1
}


# Function to synchronize files based on context-sources-map using rsync
sync_files() {
    local map_file="$1"
    local base64_dir="$2"
    local destination_dir="$3"

    # Function to decode base64 filename without padding
    decode_base64_no_padding() {
        local base64_filename="$1"
        # Add padding if necessary for base64 decoding
        while [ $((${#base64_filename} % 4)) -ne 0 ]; do
            base64_filename="${base64_filename}="
        done
        echo "$base64_filename" | base64 -d
    }

    # Check if MAP_FILE exists
    if [ ! -f "$map_file" ]; then
        echo "Error: Context-sources-map file '$map_file' does not exist."
        exit 1
    fi

    # Read the context-sources-map file line by line
    while IFS=',' read -r base64_filename destination_path; do
        # Trim whitespace
        base64_filename=$(echo "$base64_filename" | xargs)
        destination_path=$(echo "$destination_path" | xargs)

        # Decode the base64 filename
        decoded_filename=$(decode_base64_no_padding "$base64_filename")
        source_file="$base64_dir/$base64_filename"

        # Check if the source file exists
        if [ ! -f "$source_file" ]; then
            echo "Error: Source file '$source_file' does not exist."
            continue
        fi

        # Create the destination directory if it does not exist
        mkdir -p "$(dirname "$destination_dir/$destination_path")"

        # Use rsync to copy the source file to the destination path
        cp "$source_file" "$destination_dir/$destination_path"

        # Check rsync exit status
        if [ $? -eq 0 ]; then
            echo "Successfully copied '$source_file' to '$destination_dir/$destination_path'"
        else
            echo "Error: Failed to rsync '$source_file' to '$destination_path'"
        fi
    done < "$map_file"
}


# Trap any error or signal and call the error handling function
trap 'handle_error $LINENO "$BASH_COMMAND"' ERR

# Process context-sources.txt
if [ -f "$source_dir/context-sources-map.txt" ]; then
  sync_files "$source_dir/context-sources-map.txt" "$source_dir/" "$destination_dir"
fi


# Process context-refs.txt
if [ -f "$source_dir/context-refs.txt" ]; then
#    mkdir "-p" "$destination_dir"
    cd "$destination_dir"

    # Read the context-refs.txt file line by line
    while IFS=, read -r protocol destination source; do

        # Parse the url
        url_parts=($(urlparse $source))

        # Rebuild the url
        rebuilt_url=$(rebuild_url "${url_parts[@]}")

        echo "Rebuilt URL : $rebuilt_url"

        case "$protocol" in
            "git+https")
                echo "Protocol: $protocol"
                echo "Downloading $rebuilt_url"
                echo "to $destination_dir"

                username=$GIT_USERNAME
                password=$GIT_PASSWORD
                token=$GIT_TOKEN

                # Extract branch if specified as a fragment (#branch)
                branch="${rebuilt_url##*#}"
                repo="${rebuilt_url%#*}"

                # Check if a branch is specified
                if [[ "$repo" != "$rebuilt_url" ]]; then
                  echo "Branch specified: $branch"
                else
                  branch=""  # No branch specified
                  repo="$rebuilt_url"
                fi

                # Construct Git clone URL based on available authentication credentials
                if [ -n "$token" ]; then
                    if [[ $token == github_pat_* || $token == glpat* ]]; then
                        username="oauth2"
                        password="$token"
                    else
                        username="$token"
                        password="x-oauth-basic"
                    fi
                    clone_url="https://$username:$password@$repo"
                elif [ -n "$username" ] && [ -n "$password" ]; then
                    clone_url="https://$username:$password@$repo"
                else
                    clone_url="https://$repo"
                fi

                # Clone repository with or without branch
                if [ -n "$branch" ]; then
                    git clone --branch "$branch" "$clone_url" "$tmp_dir"
                else
                    git clone "$clone_url" "$tmp_dir"
                fi

                # copy temp in destination_dir
                echo "========= Copying $tmp_dir to $destination_dir ========="
                cp -a "$tmp_dir/." "$destination_dir/"
                echo "========= Files in temp dir ========="
                ls -l "$tmp_dir"
                echo "========= Files in shared dir ========="
                ls -l "$destination_dir"

                # Remove temp
                echo "========= Removing $tmp_dir ========="
                rm -rf "$tmp_dir"

            # if fragment do checkout of tag version.
            ;;
            "zip+s3") # for now accept a zip file - check if file is a zip, unpack zip
                echo "Protocol: $protocol"
                echo "Downloading s3/$rebuilt_url"
                echo "to $destination_dir/$destination"
                env MC_HOST_s3="$MC_HOST" mc cp "s3/$rebuilt_url" "$destination_dir/$destination"
                unzip "$destination_dir/$destination" -d "$destination_dir"
                ;;
            "zip+http" | "zip+https") # for now accept only zip file - check if file is a zip. unpack zip
                echo "Protocol: $protocol"
                # strip zip+
                stripped_source="${source#zip+}"
                echo "Downloading $stripped_source"
                echo "to $destination_dir/$destination"
                curl -o "$destination_dir/$destination" -L "$stripped_source"
                unzip "$destination_dir/$destination" -d "$destination_dir"
                ;;
            "http" | "https")
                echo "Protocol: $protocol"
                echo "Downloading $source"
                echo "to $destination_dir/$destination"
                curl -o "$destination_dir/$destination" -L "$source"
                ;;
            "s3") # for now accept a folder/path
                echo "Protocol: $protocol"
                echo "Downloading s3/$rebuilt_url"
                echo "to $destination_dir/$destination"
                env MC_HOST_s3="$MC_HOST" mc cp --recursive --debug "s3/$rebuilt_url" "$destination_dir/$destination"
                ;;
            # Add more cases for other protocols as needed
            *)
                echo "Unknown protocol: $protocol"
                exit 1
                ;;
        esac
    done < "$source_dir/context-refs.txt"
fi

ls -1l "$destination_dir"

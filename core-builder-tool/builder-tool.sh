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

minio="minio"

# Error handling function
handle_error() {
    local lineno=$1
    local message=$2
    echo "Error: $message at line $lineno"
    exit 1
}

# Trap any error or signal and call the error handling function
trap 'handle_error $LINENO "$BASH_COMMAND"' ERR

# Copy everything from source directory to destination directory, excluding context-refs.txt
rsync -av --exclude='context-refs.txt' --exclude='Dockerfile' --include='.*' "$source_dir/" "$destination_dir/"

# Process context-refs.txt
while IFS=, read -r protocol destination source; do

    # Parse the url
    url_parts=($(urlparse $source))

    # Rebuild the url
    rebuilt_url=$(rebuild_url "${url_parts[@]}")

    echo "Rebuilt URL : $rebuilt_url"

    case "$protocol" in
        "git+https")
            username=$GIT_USERNAME
            password=$GIT_PASSWORD
            token=$GIT_TOKEN

            # Construct Git clone URL based on available authentication credentials
            if [ -n "$token" ]; then
                if [[ $token == github_pat_* || $token == glpat* ]]; then
                    username="oauth2"
                    password="$token"
                else
                    username="$token"
                    password="x-oauth-basic"
                fi
                git clone "https://$username:$password@$rebuilt_url" "$destination_dir/$destination"
            elif [ -n "$username" ] && [ -n "$password" ]; then
                git clone "https://$username:$password@$rebuilt_url" "$destination_dir/$destination"
            else
                git clone "https://$rebuilt_url" "$destination_dir/$destination"
            fi
	    # if fragment do checkout of tag version.
	    ;;
        "zip+s3") # for now accept a zip file - check if file is a zip, unpack zip
	    mc alias set $minio $S3_ENDPOINT_URL $AWS_ACCESS_KEY_ID $AWS_SECRET_ACCESS_KEY
            mc cp "$minio/$source" "$destination_dir/$destination"
            unzip "$destination_dir/$destination"
            ;;
        "http" | "https") # for now accept only zip file - check if file is a zip. unpack zip
            curl -o "$destination_dir/$destination" -L "$source"
            unzip "$destination_dir/$destination"
            ;;
        # Add more cases for other protocols as needed
        *)
            echo "Unknown protocol: $protocol"
            exit 1
            ;;
    esac
done < "$source_dir/context-refs.txt"

ls "$destination_dir"

#!/bin/sh
set -eu

cat <<'EOF'
# Configuration


| Module | Environment Variable | Default |
|--------|----------------------|---------|
EOF

find . -type f \
  \( -path "*/src/main/resources/*.yaml" -o -path "*/src/main/resources/*.yml" \) |
while read -r file; do

  folder=$(echo "$file" | sed -E 's#.*/([^/]+)/src/main/resources/.*#\1#')

  grep -oE '\$\{[A-Za-z_][A-Za-z0-9_]*(:[^}]*)?\}' "$file" |
  while read -r placeholder; do

    expr=$(echo "$placeholder" | sed -E 's/^\$\{//; s/\}$//')

    env=$(echo "$expr" | cut -d: -f1)
    default=""

    case "$expr" in
      *:*)
        default=$(echo "$expr" | cut -d: -f2-)
        ;;
    esac

    printf '%s| `%s` | `%s` |\n' \
      "$folder" \
      "$env" \
      "$default"

  done

done |
sort -t'|' -k1,1 |
sed 's/^[^|]*|/| &/' 
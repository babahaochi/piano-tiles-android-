#!/usr/bin/env sh
set -e

# Gradle wrapper script
# This script is used to invoke the Gradle wrapper from the command line.

# Determine the directory of the script
DIR="$(cd "$(dirname "$0")" && pwd)"

# Execute the Gradle wrapper
exec "$DIR/gradlew" "$@"
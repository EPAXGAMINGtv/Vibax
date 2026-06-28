#!/bin/bash
# Build script for StorageAPI
# Usage: ./build.sh [clean|fat|all]

set -e

cd "$(dirname "$0")"

case "$1" in
    clean)
        echo "Cleaning build directory..."
        ./gradlew clean
        ;;
    fat)
        echo "Building fat JAR..."
        ./gradlew fatJar
        ;;
    all|*)
        echo "Building all JARs..."
        ./gradlew clean build fatJar
        ;;
esac

echo ""
echo "Build complete!"
echo "JAR files are in: build/libs/"
ls -lh build/libs/*.jar 2>/dev/null || echo "No JAR files found"

#!/bin/bash

# Nunki Helper Script
# Usage: ./nunki-helper.sh [command]

COMMAND=$1

case "$COMMAND" in
    "test")
        echo "Running tests..."
        ./mvnw test
        ;;
    "build:offline")
        echo "Running offline build..."
        ./mvnw clean install -o
        ;;
    "build:native")
        echo "Building GraalVM native image..."
        ./mvnw -Pnative native:compile
        ;;
    "integration:quasar")
        echo "Running integration tests with Quasar (to be defined)..."
        # Placeholder for Quasar integration tests
        echo "Not implemented yet."
        ;;
    *)
        echo "Usage: $0 {test|build:offline|build:native|integration:quasar}"
        exit 1
        ;;
esac
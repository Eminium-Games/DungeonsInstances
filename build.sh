#!/bin/bash

# Define build directory
BUILD_DIR="build"

# Function to build the project
build_project() {
    echo "Building project..."

    # Create build directory if it doesn't exist
    mkdir -p "$BUILD_DIR"

    # Clean previous builds
    rm -rf "$BUILD_DIR"/*

    # Compile and package the project
    mvn clean package -DskipTests -o -T 1C -q

    # Move the generated JAR to the build directory
    JAR_NAME="Dungeon Instances-1.0.0.jar"
    if [ -f "target/$JAR_NAME" ]; then
        mv "target/$JAR_NAME" "$BUILD_DIR/"
        echo "Build successful. JAR moved to $BUILD_DIR/$JAR_NAME"
    else
        echo "Build failed. JAR not found in target directory."
        return 1
    fi

    # Check if destination directory exists before copying
    DEST_DIR="/var/lib/pelican/volumes/1edd5643-50bb-41e1-8b3f-d4a328a858fc/plugins"
    if [ -d "$DEST_DIR" ]; then
        sudo cp "$BUILD_DIR/$JAR_NAME" "$DEST_DIR/$JAR_NAME"
        echo "JAR copied to $DEST_DIR/$JAR_NAME"
    else
        echo "Destination directory does not exist: $DEST_DIR"
        return 1
    fi
}

# Check if inotifywait is installed
if ! command -v inotifywait &> /dev/null
then
    echo "inotifywait could not be found. Please install inotify-tools."
    exit 1
fi

build_project

# Watch for file changes and rebuild automatically
while true; do
    inotifywait -e modify,create,delete -r src/ pom.xml build.sh > /dev/null 2>&1
    build_project
    echo "Waiting for file changes..."
done
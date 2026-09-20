#!/bin/bash
set -e

echo "Building Vega compiler..."

rm -rf build
mkdir -p build/classes

find src/main/java -name "*.java" > build/sources.txt

javac -cp "libs/*" \
      -d build/classes \
      @build/sources.txt

echo "Compilation successful."

jar --create \
    --file build/vega-compiler.jar \
    -C build/classes .

echo "Vega compiler built:"
echo "  build/vega-compiler.jar"

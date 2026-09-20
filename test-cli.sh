#!/bin/bash

# Simple CLI test script

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

echo "═══════════════════════════════════════════════════════════════"
echo "Vega CLI - Test Suite"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Test 1: Version
echo "Test 1: Version"
java -cp vega.jar:libs/* com.vega.cli.VegaCLI --version
echo "✓ PASS"
echo ""

# Test 2: Help
echo "Test 2: Help"
java -cp vega.jar:libs/* com.vega.cli.VegaCLI --help
echo "✓ PASS"
echo ""

# Test 3: Compile example
echo "Test 3: Compile hello.vg"
rm -rf out
java -cp vega.jar:libs/* com.vega.cli.VegaCLI compile examples/hello.vg
if [ -f "out/Main.class" ]; then
    echo "✓ PASS - hello.vg compiled"
else
    echo "✗ FAIL - hello.vg not compiled"
    exit 1
fi
echo ""

# Test 4: Run example
echo "Test 4: Run hello.vg"
java -cp vega.jar:libs/* com.vega.cli.VegaCLI run examples/hello.vg
echo "✓ PASS"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo "All CLI tests passed! ✓"
echo "═══════════════════════════════════════════════════════════════"

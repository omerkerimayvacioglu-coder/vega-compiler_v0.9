#!/bin/bash

# vegac CLI Test Suite
# Tests all vegac commands and options

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VEGAC="$SCRIPT_DIR/vegac"
TEMP_DIR=$(mktemp -d)

echo "=========================================="
echo "vegac CLI Test Suite"
echo "=========================================="
echo ""

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PASS=0
FAIL=0

# Test function
test_vegac() {
    local name=$1
    local cmd=$2
    local expect_success=${3:-true}
    
    echo -n "Testing: $name ... "
    
    if eval "$cmd" > /dev/null 2>&1; then
        if [ "$expect_success" = true ]; then
            echo -e "${GREEN}✓ PASS${NC}"
            ((PASS++))
        else
            echo -e "${RED}✗ FAIL${NC} (expected to fail)"
            ((FAIL++))
        fi
    else
        if [ "$expect_success" = false ]; then
            echo -e "${GREEN}✓ PASS${NC} (expected to fail)"
            ((PASS++))
        else
            echo -e "${RED}✗ FAIL${NC}"
            ((FAIL++))
        fi
    fi
}

echo "=== Basic Commands ==="
test_vegac "vegac exists" "test -f $VEGAC"
test_vegac "vegac is executable" "test -x $VEGAC"
test_vegac "vegac --help" "$VEGAC --help"
test_vegac "vegac -h" "$VEGAC -h"
test_vegac "vegac help" "$VEGAC help"
test_vegac "vegac --version" "$VEGAC --version"
test_vegac "vegac -v" "$VEGAC -v"
test_vegac "vegac stdlib" "$VEGAC stdlib"

echo ""
echo "=== Build Command ==="

# Create test file
TEST_FILE="$TEMP_DIR/test.vg"
cat > "$TEST_FILE" << 'EOF'
package test

import stdlib.io.*
import stdlib.string.*

fn main() {
    println("Hello from vegac test!")
    let text = "Vega"
    println("Length: \(len(text))")
}
EOF

test_vegac "vegac build test.vg" "$VEGAC build $TEST_FILE"
test_vegac "vegac compile test.vg" "$VEGAC compile $TEST_FILE"
test_vegac "vegac build with --out" "$VEGAC build $TEST_FILE --out $TEMP_DIR/output"
test_vegac "output directory created" "test -d $TEMP_DIR/output"

echo ""
echo "=== Error Cases ==="
test_vegac "vegac build without file" "$VEGAC build" false
test_vegac "vegac run without file" "$VEGAC run" false
test_vegac "vegac unknown command" "$VEGAC unknown_cmd" false
test_vegac "vegac build nonexistent file" "$VEGAC build /nonexistent/file.vg" false

echo ""
echo "=== Debug Commands ==="
test_vegac "vegac tokens" "$VEGAC tokens $TEST_FILE"
test_vegac "vegac ast" "$VEGAC ast $TEST_FILE"

echo ""
echo "=== Java Checks ==="
test_vegac "Java is installed" "command -v java"
test_vegac "Java version check" "java -version 2>&1 | grep -q '[0-9]'"

echo ""
echo "=== File Permissions ==="
ls_output=$(ls -la "$VEGAC")
echo "vegac permissions: $ls_output"

if [[ "$ls_output" == *"x"* ]]; then
    echo -e "${GREEN}✓${NC} Script is executable"
    ((PASS++))
else
    echo -e "${RED}✗${NC} Script is NOT executable"
    ((FAIL++))
fi

echo ""
echo "=========================================="
echo "Test Results"
echo "=========================================="
echo -e "Passed: ${GREEN}$PASS${NC}"
echo -e "Failed: ${RED}$FAIL${NC}"
echo -e "Total:  $(($PASS + $FAIL))"

# Cleanup
rm -rf "$TEMP_DIR"

if [ $FAIL -eq 0 ]; then
    echo -e "\n${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "\n${RED}✗ Some tests failed${NC}"
    exit 1
fi

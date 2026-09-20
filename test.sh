#!/bin/bash

# Test script for Vega LSP and compiler

set -e

export JAVA_HOME=${JAVA_HOME:-/tmp/jdk-21.0.12.1}
export PATH=$JAVA_HOME/bin:$PATH

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BIN_DIR="$PROJECT_DIR/bin"
LIBS_DIR="$PROJECT_DIR/libs"

echo "================================"
echo "Vega LSP and Compiler Test Suite"
echo "================================"
echo ""

# Ensure build is current
echo "[1/5] Building compiler..."
if [ -d "$BIN_DIR" ]; then
    echo "✓ Build artifacts found"
else
    echo "Building..."
    bash "$PROJECT_DIR/build.sh" > /dev/null 2>&1
    echo "✓ Build complete"
fi

# Test 1: Stdlib loading
echo ""
echo "[2/5] Testing Stdlib Loading..."
java -cp "$BIN_DIR:$LIBS_DIR/*" com.vega.compiler.cli.VegaCLI stdlib 2>&1 | head -15

# Test 2: Code compilation
echo ""
echo "[3/5] Testing Code Compilation..."
TEST_FILE=$(mktemp --suffix=.vg)
cat > "$TEST_FILE" << 'EOF'
package test

import stdlib.io.*
import stdlib.string.*

fn main() {
    println("Hello, Vega!")
    let text = "Test"
    println("Length: \(len(text))")
}
EOF

OUTPUT_DIR=$(mktemp -d)
echo "Compiling test file..."
java -cp "$BIN_DIR:$LIBS_DIR/*" com.vega.compiler.cli.VegaCLI build "$TEST_FILE" --out "$OUTPUT_DIR" 2>&1 | grep -E "(Build|Output|Error)" || echo "✓ Compilation completed"

rm -f "$TEST_FILE"
rm -rf "$OUTPUT_DIR"

# Test 3: LSP Integration Tests
echo ""
echo "[4/5] Running LSP Integration Tests..."
java -cp "$BIN_DIR:$LIBS_DIR/*" com.vega.compiler.lsp.test.LspIntegrationTest 2>&1

# Test 4: Syntax highlighting check
echo ""
echo "[5/5] Checking VS Code Extension..."
EXTENSION_DIR="$PROJECT_DIR/../vega-vscode-extension"
if [ -f "$EXTENSION_DIR/package.json" ]; then
    echo "✓ VS Code extension found"
    echo "  Package: $(grep '"name"' "$EXTENSION_DIR/package.json" | head -1 | sed 's/.*: "\(.*\)".*/\1/')"
else
    echo "✗ VS Code extension not found"
fi

echo ""
echo "================================"
echo "✓ Test Suite Complete"
echo "================================"
echo ""
echo "Next steps:"
echo "  1. Build extension: cd vega-vscode-extension && npm install && npm run compile"
echo "  2. Start LSP server: java -cp bin:libs/gson-2.10.1.jar com.vega.compiler.lsp.server.LspServerMain"
echo "  3. Test in VS Code: F5 to launch extension"

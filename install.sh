#!/bin/bash

# Vega Compiler Global Installation Script
# Installs vegac command globally and sets up environment

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VEGAC_SOURCE="$SCRIPT_DIR/vegac"
INSTALL_METHOD=${1:-"path"}  # path, symlink, or copy

echo "=========================================="
echo "Vega Compiler Global Installation"
echo "=========================================="
echo ""
echo "Vega Compiler Directory: $SCRIPT_DIR"
echo "vegac script location: $VEGAC_SOURCE"

# First, ensure compiler is built
echo ""
echo "=== Checking Compiler Build ==="
if [ ! -d "$SCRIPT_DIR/bin" ] || [ ! -f "$SCRIPT_DIR/bin/com/vega/compiler/cli/VegaCLI.class" ]; then
    echo "⚠ Compiler not built or incomplete"
    echo "Running build.sh to compile..."
    bash "$SCRIPT_DIR/build.sh"
    echo "✓ Build complete"
fi

# Check if vegac exists
if [ ! -f "$VEGAC_SOURCE" ]; then
    echo "⚠ vegac script not found at $VEGAC_SOURCE"
    echo "Creating basic vegac script..."
    
    cat > "$VEGAC_SOURCE" << 'EOF'
#!/bin/bash
# Vega Compiler CLI - vegac
# Auto-generated script

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CLASSPATH="$SCRIPT_DIR/bin:$SCRIPT_DIR/libs/*"

if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    echo "Vega Compiler CLI"
    echo "Usage: vegac <command>"
    echo "Commands: build, run, compile, stdlib"
    exit 0
fi

if [ "$1" = "--version" ] || [ "$1" = "-v" ]; then
    echo "Vega Compiler v0.1.0"
    exit 0
fi

if [ "$1" = "build" ] || [ "$1" = "compile" ]; then
    java -cp "$CLASSPATH" com.vega.compiler.cli.VegaCLI build "${@:2}"
elif [ "$1" = "run" ]; then
    java -cp "$CLASSPATH" com.vega.compiler.cli.VegaCLI run "${@:2}"
elif [ "$1" = "stdlib" ]; then
    java -cp "$CLASSPATH" com.vega.compiler.cli.VegaCLI stdlib
else
    echo "Usage: vegac <command>"
    echo "Try: vegac --help"
    exit 1
fi
EOF
    
    chmod +x "$VEGAC_SOURCE"
    echo "✓ vegac script created"
fi

# Make executable
chmod +x "$VEGAC_SOURCE"

# Detect shell
SHELL_PROFILE=""
if [ -f "$HOME/.bashrc" ]; then
    SHELL_PROFILE="$HOME/.bashrc"
elif [ -f "$HOME/.zshrc" ]; then
    SHELL_PROFILE="$HOME/.zshrc"
elif [ -f "$HOME/.bash_profile" ]; then
    SHELL_PROFILE="$HOME/.bash_profile"
else
    echo "Warning: Could not detect shell profile"
fi

echo "Installation Method: $INSTALL_METHOD"
echo "Vega Directory: $SCRIPT_DIR"
echo "Shell Profile: $SHELL_PROFILE"
echo ""

case "$INSTALL_METHOD" in
    path)
        echo "=== Installing via PATH ==="
        echo "Adding $SCRIPT_DIR to PATH..."
        
        if [ -n "$SHELL_PROFILE" ]; then
            # Check if already in PATH
            if grep -q "export PATH=.*vega-compiler-main" "$SHELL_PROFILE"; then
                echo "✓ Already in PATH"
            else
                # Add to PATH
                echo "" >> "$SHELL_PROFILE"
                echo "# Vega Compiler" >> "$SHELL_PROFILE"
                echo "export PATH=\"$SCRIPT_DIR:\$PATH\"" >> "$SHELL_PROFILE"
                echo "✓ Added to $SHELL_PROFILE"
            fi
            
            echo ""
            echo "To apply changes, run:"
            echo "  source $SHELL_PROFILE"
            echo ""
            echo "Or restart your terminal."
        fi
        ;;
    
    symlink)
        echo "=== Installing via Symlink ==="
        
        if [ ! -d "/usr/local/bin" ]; then
            echo "Error: /usr/local/bin does not exist"
            exit 1
        fi
        
        if [ -f "/usr/local/bin/vegac" ]; then
            echo "Removing existing /usr/local/bin/vegac..."
            sudo rm /usr/local/bin/vegac
        fi
        
        echo "Creating symlink..."
        sudo ln -s "$VEGAC_SOURCE" /usr/local/bin/vegac
        
        echo "✓ Symlink created"
        echo ""
        echo "vegac is now available globally!"
        ;;
    
    copy)
        echo "=== Installing via Copy ==="
        
        if [ ! -d "/usr/local/bin" ]; then
            echo "Error: /usr/local/bin does not exist"
            exit 1
        fi
        
        if [ -f "/usr/local/bin/vegac" ]; then
            echo "Removing existing /usr/local/bin/vegac..."
            sudo rm /usr/local/bin/vegac
        fi
        
        echo "Copying vegac..."
        sudo cp "$VEGAC_SOURCE" /usr/local/bin/vegac
        sudo chmod +x /usr/local/bin/vegac
        
        echo "✓ vegac copied to /usr/local/bin"
        echo ""
        echo "vegac is now available globally!"
        ;;
    
    *)
        echo "Unknown method: $INSTALL_METHOD"
        echo "Use: install.sh [path|symlink|copy]"
        exit 1
        ;;
esac

echo ""
echo "=========================================="
echo "Verifying Installation"
echo "=========================================="

# Source shell profile if PATH method
if [ "$INSTALL_METHOD" = "path" ] && [ -n "$SHELL_PROFILE" ]; then
    export PATH="$SCRIPT_DIR:$PATH"
fi

# Test vegac
if command -v vegac &> /dev/null; then
    echo "✓ vegac is accessible"
    vegac --version
    echo ""
    echo "✓ Installation successful!"
else
    echo "⚠ vegac not yet in PATH"
    echo ""
    if [ "$INSTALL_METHOD" = "path" ]; then
        echo "Please run: source $SHELL_PROFILE"
        echo "Then verify with: vegac --version"
    fi
fi

echo ""
echo "=========================================="
echo "Next Steps"
echo "=========================================="
echo ""
echo "1. Verify installation:"
echo "   vegac --version"
echo ""
echo "2. View help:"
echo "   vegac --help"
echo ""
echo "3. Build a program:"
echo "   vegac build main.vg"
echo ""
echo "4. Run a program:"
echo "   vegac run main.vg"
echo ""
echo "=========================================="

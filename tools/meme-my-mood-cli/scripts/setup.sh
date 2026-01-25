#!/usr/bin/env bash
#
# Set up the meme-my-mood-cli development environment.
# Creates a Python virtual environment and installs dependencies.
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
VENV_PATH="$PROJECT_ROOT/.venv"
REQUIRE_PYTHON="3.10"

echo "Setting up meme-my-mood-cli..."
echo ""

# Check Python version
if ! command -v python3 &> /dev/null; then
    echo "Error: Python not found. Please install Python $REQUIRE_PYTHON or later."
    exit 1
fi

PYTHON_VERSION=$(python3 -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')")
echo "Found Python $PYTHON_VERSION"

# Check if venv already exists
if [ -d "$VENV_PATH" ]; then
    echo "Virtual environment already exists at $VENV_PATH"
    read -p "Recreate? (y/N) " response
    if [[ "$response" =~ ^[Yy]$ ]]; then
        rm -rf "$VENV_PATH"
    else
        echo "Using existing virtual environment."
    fi
fi

# Create virtual environment if needed
if [ ! -d "$VENV_PATH" ]; then
    echo "Creating virtual environment..."
    python3 -m venv "$VENV_PATH"
    echo "Virtual environment created at $VENV_PATH"
fi

# Activate and install
echo "Activating virtual environment..."
source "$VENV_PATH/bin/activate"

echo "Upgrading pip..."
python -m pip install --upgrade pip

echo "Installing meme-my-mood-cli in development mode..."
cd "$PROJECT_ROOT"
pip install -e ".[dev]"

echo ""
echo "============================================================"
echo "Setup complete!"
echo "============================================================"
echo ""
echo "To activate the virtual environment, run:"
echo "  source .venv/bin/activate"
echo ""
echo "Then you can use the CLI:"
echo "  meme-cli --help"
echo "  meme-cli auth login"
echo "  meme-cli annotate ./my-memes"
echo ""

"""Configuration and credential management for riposte-cli."""

import json
import os
from pathlib import Path


def get_config_dir() -> Path:
    """Get the configuration directory for the CLI.
    
    Returns:
        Path to the configuration directory.
    """
    # Use XDG_CONFIG_HOME on Linux, APPDATA on Windows, ~/Library on macOS
    if os.name == "nt":
        config_home = Path(os.environ.get("APPDATA", Path.home()))
    elif os.name == "posix":
        xdg_config = os.environ.get("XDG_CONFIG_HOME")
        if xdg_config:
            config_home = Path(xdg_config)
        elif Path.home().joinpath("Library").exists():
            config_home = Path.home() / "Library" / "Application Support"
        else:
            config_home = Path.home() / ".config"
    else:
        config_home = Path.home()
    
    config_dir = config_home / "riposte-cli"
    config_dir.mkdir(parents=True, exist_ok=True)
    return config_dir


def get_config_file() -> Path:
    """Get the path to the config file."""
    return get_config_dir() / "config.json"


def load_config() -> dict:
    """Load configuration from disk.
    
    Returns:
        Dictionary with configuration values.
    """
    config_file = get_config_file()
    if config_file.exists():
        with open(config_file, encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_config(config: dict) -> None:
    """Save configuration to disk.
    
    Args:
        config: Dictionary with configuration values.
    """
    config_file = get_config_file()
    with open(config_file, "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2)


def get_access_token() -> str | None:
    """Get the GitHub access token.
    
    Checks in order:
    1. GITHUB_TOKEN environment variable
    2. GH_TOKEN environment variable
    3. Saved config file
    
    Returns:
        The access token, or None if not found.
    """
    # Check environment variables first
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        return token
    
    token = os.environ.get("GH_TOKEN")
    if token:
        return token
    
    # Check saved config
    config = load_config()
    return config.get("access_token")


def save_access_token(token: str) -> None:
    """Save the access token to config file.
    
    Args:
        token: The GitHub PAT to save.
    """
    config = load_config()
    config["access_token"] = token
    save_config(config)


def delete_access_token() -> None:
    """Delete the saved access token."""
    config = load_config()
    config.pop("access_token", None)
    save_config(config)


def get_default_model() -> str:
    """Get the default model to use.
    
    Returns:
        The model name (default: openai/gpt-4.1).
    """
    config = load_config()
    return config.get("default_model", "openai/gpt-4.1")


def save_default_model(model: str) -> None:
    """Save the default model.
    
    Args:
        model: The model name to save.
    """
    config = load_config()
    config["default_model"] = model
    save_config(config)

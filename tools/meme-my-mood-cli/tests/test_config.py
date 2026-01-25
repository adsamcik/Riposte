"""Tests for configuration module."""

import json
import os
import tempfile
from pathlib import Path
from unittest import mock

import pytest

from meme_my_mood_cli.config import (
    get_access_token,
    get_config_dir,
    load_credentials,
    save_credentials,
)


class TestGetConfigDir:
    """Tests for get_config_dir function."""

    def test_windows_config_dir(self) -> None:
        """Test config directory on Windows."""
        with mock.patch("sys.platform", "win32"):
            with mock.patch.dict(os.environ, {"APPDATA": "C:\\Users\\Test\\AppData\\Roaming"}):
                config_dir = get_config_dir()
                assert "meme-my-mood" in str(config_dir)

    def test_linux_config_dir(self) -> None:
        """Test config directory on Linux."""
        with mock.patch("sys.platform", "linux"):
            with mock.patch.dict(os.environ, {"XDG_CONFIG_HOME": "/home/test/.config"}):
                config_dir = get_config_dir()
                assert "meme-my-mood" in str(config_dir)


class TestCredentials:
    """Tests for credential management functions."""

    def test_save_and_load_credentials(self, tmp_path: Path) -> None:
        """Test saving and loading credentials."""
        with mock.patch(
            "meme_my_mood_cli.config.get_config_dir",
            return_value=tmp_path,
        ):
            credentials = {
                "access_token": "test_token_123",
                "username": "testuser",
            }
            
            save_credentials(credentials)
            loaded = load_credentials()
            
            assert loaded is not None
            assert loaded["access_token"] == "test_token_123"
            assert loaded["username"] == "testuser"

    def test_load_missing_credentials(self, tmp_path: Path) -> None:
        """Test loading when no credentials file exists."""
        with mock.patch(
            "meme_my_mood_cli.config.get_config_dir",
            return_value=tmp_path,
        ):
            result = load_credentials()
            assert result is None

    def test_get_access_token(self, tmp_path: Path) -> None:
        """Test getting access token from credentials."""
        with mock.patch(
            "meme_my_mood_cli.config.get_config_dir",
            return_value=tmp_path,
        ):
            credentials = {"access_token": "my_secret_token"}
            save_credentials(credentials)
            
            token = get_access_token()
            assert token == "my_secret_token"

    def test_get_access_token_not_authenticated(self, tmp_path: Path) -> None:
        """Test getting access token when not authenticated."""
        with mock.patch(
            "meme_my_mood_cli.config.get_config_dir",
            return_value=tmp_path,
        ):
            token = get_access_token()
            assert token is None

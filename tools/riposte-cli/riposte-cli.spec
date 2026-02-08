# -*- mode: python ; coding: utf-8 -*-
"""
PyInstaller spec file for building a portable single-file executable.

Build commands:
    Windows: pyinstaller meme-cli.spec
    macOS:   pyinstaller meme-cli.spec
    Linux:   pyinstaller meme-cli.spec

Output: dist/meme-cli.exe (Windows) or dist/meme-cli (macOS/Linux)
"""

import sys
from pathlib import Path

block_cipher = None

# Get the source directory
src_dir = Path('src/riposte_cli')

a = Analysis(
    ['src/riposte_cli/main.py'],
    pathex=[],
    binaries=[],
    datas=[],
    hiddenimports=[
        'riposte_cli',
        'riposte_cli.commands',
        'riposte_cli.commands.auth',
        'riposte_cli.commands.annotate',
        'riposte_cli.config',
        'riposte_cli.copilot',
        # Dependencies
        'click',
        'httpx',
        'httpcore',
        'rich',
        'rich.console',
        'rich.panel',
        'rich.progress',
        'tenacity',
        'filetype',
        'PIL',
        'github_copilot_sdk',
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[
        # Exclude unnecessary modules to reduce size
        'tkinter',
        'unittest',
        'pydoc',
    ],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name='meme-cli',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,  # Compress with UPX if available
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,  # CLI app needs console
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=None,  # Add icon path here if desired: 'assets/icon.ico'
)

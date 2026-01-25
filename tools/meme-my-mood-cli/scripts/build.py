#!/usr/bin/env python3
"""
Build script for creating portable meme-cli executables.

Usage:
    python scripts/build.py          # Build for current platform
    python scripts/build.py --clean  # Clean build artifacts first
"""

import argparse
import platform
import shutil
import subprocess
import sys
from pathlib import Path


def get_platform_name() -> str:
    """Get a normalized platform name for the output."""
    system = platform.system().lower()
    machine = platform.machine().lower()
    
    if system == "windows":
        return "windows-x64" if "64" in machine else "windows-x86"
    elif system == "darwin":
        return "macos-arm64" if machine == "arm64" else "macos-x64"
    elif system == "linux":
        return "linux-x64" if "64" in machine else "linux-x86"
    return f"{system}-{machine}"


def clean_build_artifacts(project_root: Path) -> None:
    """Remove build artifacts."""
    dirs_to_remove = ["build", "dist", "__pycache__"]
    
    for dir_name in dirs_to_remove:
        dir_path = project_root / dir_name
        if dir_path.exists():
            print(f"Removing {dir_path}...")
            shutil.rmtree(dir_path)
    
    # Remove .pyc files
    for pyc_file in project_root.rglob("*.pyc"):
        pyc_file.unlink()
    
    # Remove .spec backup files
    for spec_bak in project_root.glob("*.spec.bak"):
        spec_bak.unlink()


def build_executable(project_root: Path) -> Path:
    """Build the portable executable."""
    spec_file = project_root / "meme-cli.spec"
    
    if not spec_file.exists():
        print(f"Error: {spec_file} not found")
        sys.exit(1)
    
    print(f"Building portable executable for {get_platform_name()}...")
    print("-" * 50)
    
    # Run PyInstaller
    result = subprocess.run(
        [sys.executable, "-m", "PyInstaller", str(spec_file), "--clean"],
        cwd=project_root,
    )
    
    if result.returncode != 0:
        print("Build failed!")
        sys.exit(1)
    
    # Determine output filename
    exe_name = "meme-cli.exe" if platform.system() == "Windows" else "meme-cli"
    exe_path = project_root / "dist" / exe_name
    
    if not exe_path.exists():
        print(f"Error: Expected output {exe_path} not found")
        sys.exit(1)
    
    # Create platform-specific named copy
    platform_name = get_platform_name()
    if platform.system() == "Windows":
        final_name = f"meme-cli-{platform_name}.exe"
    else:
        final_name = f"meme-cli-{platform_name}"
    
    final_path = project_root / "dist" / final_name
    shutil.copy2(exe_path, final_path)
    
    print("-" * 50)
    print(f"✓ Build complete!")
    print(f"  Output: {final_path}")
    print(f"  Size: {final_path.stat().st_size / (1024 * 1024):.1f} MB")
    
    return final_path


def main() -> None:
    parser = argparse.ArgumentParser(description="Build portable meme-cli executable")
    parser.add_argument("--clean", action="store_true", help="Clean build artifacts first")
    args = parser.parse_args()
    
    project_root = Path(__file__).parent.parent
    
    if args.clean:
        clean_build_artifacts(project_root)
    
    # Check if PyInstaller is installed
    try:
        import PyInstaller
    except ImportError:
        print("PyInstaller not found. Installing...")
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "pyinstaller>=6.0.0"],
            check=True,
        )
    
    build_executable(project_root)


if __name__ == "__main__":
    main()

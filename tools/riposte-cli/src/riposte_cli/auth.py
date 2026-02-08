"""Authentication commands for riposte-cli.

With the Copilot SDK, authentication is handled by the Copilot CLI.
This module provides commands to check and verify the CLI installation.
"""

import shutil
import subprocess

import click


def check_copilot_cli() -> tuple[bool, str | None]:
    """Check if Copilot CLI is installed and accessible.
    
    Returns:
        Tuple of (is_available, version or error message).
    """
    # Check if 'copilot' is in PATH
    copilot_path = shutil.which("copilot")
    if not copilot_path:
        return False, "Copilot CLI not found in PATH"
    
    try:
        result = subprocess.run(
            ["copilot", "--version"],
            capture_output=True,
            text=True,
            timeout=10,
        )
        if result.returncode == 0:
            version = result.stdout.strip() or result.stderr.strip()
            return True, version
        return False, f"CLI returned error: {result.stderr}"
    except subprocess.TimeoutExpired:
        return False, "CLI timed out"
    except Exception as e:
        return False, f"Error running CLI: {e}"


@click.group()
def auth():
    """Manage Copilot CLI setup and status."""
    pass


@auth.command()
def status():
    """Check Copilot CLI installation status."""
    click.echo("Checking Copilot CLI installation...")
    
    is_available, result = check_copilot_cli()
    
    if is_available:
        click.secho(f"✓ Copilot CLI installed: {result}", fg="green")
        click.echo("\nYou're ready to use meme-cli!")
        click.echo("Run: meme-cli annotate <folder>")
    else:
        click.secho(f"❌ {result}", fg="red")
        click.echo("\nTo install Copilot CLI:")
        click.echo("  https://github.com/github/copilot-cli")
        click.echo("\nMake sure 'copilot' is in your PATH.")


@auth.command()
def check():
    """Verify Copilot CLI can connect."""
    click.echo("Checking Copilot CLI connectivity...")
    
    is_available, version = check_copilot_cli()
    
    if not is_available:
        click.secho(f"❌ Copilot CLI not available: {version}", fg="red")
        click.echo("\nInstall from: https://github.com/github/copilot-cli")
        raise click.Abort()
    
    click.secho(f"✓ Copilot CLI: {version}", fg="green")
    
    # Try to ping the CLI
    click.echo("Testing connection...")
    try:
        import asyncio
        from copilot import CopilotClient
        
        async def test_connection():
            async with CopilotClient() as client:
                response = await client.ping("health check")
                return response
        
        response = asyncio.run(test_connection())
        click.secho("✓ Connection successful", fg="green")
        click.echo(f"  Server timestamp: {response.get('timestamp', 'unknown')}")
    except Exception as e:
        click.secho(f"❌ Connection failed: {e}", fg="red")
        click.echo("\nMake sure you're logged in to Copilot CLI:")
        click.echo("  copilot auth login")

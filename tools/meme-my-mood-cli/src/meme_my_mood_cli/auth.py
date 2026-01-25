"""Authentication commands for meme-my-mood-cli."""

import click
import httpx

from .config import (
    delete_access_token,
    get_access_token,
    save_access_token,
)


def verify_token(token: str) -> tuple[bool, str | None]:
    """Verify a GitHub token by making a test API call.
    
    Args:
        token: The GitHub PAT to verify.
        
    Returns:
        Tuple of (is_valid, username or error message).
    """
    try:
        with httpx.Client(timeout=10.0) as client:
            # Check user identity
            response = client.get(
                "https://api.github.com/user",
                headers={
                    "Authorization": f"Bearer {token}",
                    "Accept": "application/vnd.github+json",
                    "X-GitHub-Api-Version": "2022-11-28",
                },
            )
        
        if response.status_code == 200:
            data = response.json()
            return True, data.get("login", "unknown")
        elif response.status_code == 401:
            return False, "Invalid or expired token"
        else:
            return False, f"API error: {response.status_code}"
    
    except httpx.RequestError as e:
        return False, f"Network error: {e}"


def check_models_scope(token: str) -> bool:
    """Check if the token has the 'models' scope by testing the API.
    
    Args:
        token: The GitHub PAT to check.
        
    Returns:
        True if models access is available.
    """
    try:
        with httpx.Client(timeout=10.0) as client:
            response = client.post(
                "https://models.github.ai/inference/chat/completions",
                json={
                    "model": "openai/gpt-4o-mini",
                    "messages": [{"role": "user", "content": "Hi"}],
                    "max_tokens": 1,
                },
                headers={
                    "Authorization": f"Bearer {token}",
                    "Accept": "application/vnd.github+json",
                    "X-GitHub-Api-Version": "2022-11-28",
                    "Content-Type": "application/json",
                },
            )
        
        # If we get a 200, models access works
        # 401/403 means no access
        return response.status_code == 200
    
    except httpx.RequestError:
        return False


@click.group()
def auth():
    """Manage authentication."""
    pass


@auth.command()
@click.option(
    "--token",
    prompt="GitHub PAT",
    hide_input=True,
    help="GitHub Personal Access Token with 'models' scope.",
)
def login(token: str):
    """Login with a GitHub Personal Access Token.
    
    Create a PAT at https://github.com/settings/tokens with the 'models' scope.
    """
    click.echo("Verifying token...")
    
    # Verify the token is valid
    is_valid, result = verify_token(token)
    if not is_valid:
        click.secho(f"❌ {result}", fg="red")
        raise click.Abort()
    
    username = result
    click.echo(f"✓ Token valid for user: {username}")
    
    # Check for models scope
    click.echo("Checking GitHub Models access...")
    has_models = check_models_scope(token)
    
    if not has_models:
        click.secho(
            "⚠️  Warning: Token may not have 'models' scope.\n"
            "   Create a PAT with 'models' scope at:\n"
            "   https://github.com/settings/tokens",
            fg="yellow",
        )
    else:
        click.echo("✓ GitHub Models access confirmed")
    
    # Save the token
    save_access_token(token)
    click.secho(f"✓ Logged in as {username}", fg="green")


@auth.command()
def logout():
    """Remove saved authentication."""
    delete_access_token()
    click.secho("✓ Logged out", fg="green")


@auth.command()
def status():
    """Check authentication status."""
    token = get_access_token()
    
    if not token:
        click.echo("Not authenticated.")
        click.echo("\nTo authenticate, run:")
        click.echo("  meme-cli auth login")
        click.echo("\nOr set GITHUB_TOKEN environment variable.")
        return
    
    # Check where token came from
    import os
    if os.environ.get("GITHUB_TOKEN"):
        source = "GITHUB_TOKEN environment variable"
    elif os.environ.get("GH_TOKEN"):
        source = "GH_TOKEN environment variable"
    else:
        source = "saved config"
    
    click.echo(f"Token source: {source}")
    
    # Verify the token
    is_valid, result = verify_token(token)
    if is_valid:
        click.secho(f"✓ Authenticated as: {result}", fg="green")
        
        # Check models access
        if check_models_scope(token):
            click.secho("✓ GitHub Models access: available", fg="green")
        else:
            click.secho(
                "⚠️  GitHub Models access: not available\n"
                "   Token may be missing 'models' scope",
                fg="yellow",
            )
    else:
        click.secho(f"❌ Token invalid: {result}", fg="red")

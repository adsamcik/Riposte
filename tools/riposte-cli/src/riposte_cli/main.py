"""Main CLI entry point for riposte-cli."""

import click

from riposte_cli.auth import auth
from riposte_cli.commands.annotate import annotate


@click.group()
@click.version_option(package_name="riposte-cli")
def cli():
    """Riposte CLI - AI-powered meme annotation tool.

    Annotate meme images with emojis, titles, descriptions, and tags
    using GitHub Copilot AI. Outputs JSON sidecar files compatible
    with the Riposte Android app.

    \b
    Quick Start:
      meme-cli annotate ./memes
      meme-cli annotate ./memes --zip
      meme-cli annotate ./memes --languages en,cs,de

    \b
    Prerequisites:
      - GitHub Copilot CLI installed and in PATH
      - Authenticated via: copilot auth login
    """
    pass


# Register commands
cli.add_command(annotate)
cli.add_command(auth)


if __name__ == "__main__":
    cli()

"""Main CLI entry point for meme-my-mood-cli."""

import click

from meme_my_mood_cli.auth import auth
from meme_my_mood_cli.commands.annotate import annotate


@click.group()
@click.version_option(package_name="meme-my-mood-cli")
def cli():
    """Meme My Mood CLI - AI-powered meme annotation tool.

    Annotate meme images with emojis, titles, descriptions, and tags
    using GitHub Copilot AI. Outputs JSON sidecar files compatible
    with the Meme My Mood Android app.

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

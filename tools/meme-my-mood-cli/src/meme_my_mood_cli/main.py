"""Main CLI entry point for meme-my-mood-cli."""

import click

from meme_my_mood_cli import __version__
from meme_my_mood_cli.auth import auth
from meme_my_mood_cli.commands.annotate import annotate


@click.group()
@click.version_option(version=__version__, prog_name="meme-cli")
def cli() -> None:
    """Meme My Mood CLI - AI-powered meme annotation tool.
    
    Annotate meme images with emoji tags and metadata using GitHub Models API.
    Generate JSON sidecar files compatible with the Meme My Mood Android app.
    
    Prerequisites:
    - GitHub PAT with 'models' scope (https://github.com/settings/tokens)
    - Run 'meme-cli auth login' to authenticate
    """
    pass


cli.add_command(annotate)
cli.add_command(auth)


if __name__ == "__main__":
    cli()

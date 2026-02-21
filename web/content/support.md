---
title: "Support"
description: "Get help with Riposte"
---

## Get Help

Riposte is an open-source project. If you need help or want to report a problem, the best way is through GitHub.

### 🐛 Report a Bug

Found something broken? [Open an issue](https://github.com/adsamcik/meme-my-mood/issues/new) on GitHub with:

- A description of the problem
- Steps to reproduce it
- Your device model and Android version

### 💡 Request a Feature

Have an idea for a new feature? [Open an issue](https://github.com/adsamcik/meme-my-mood/issues/new) and describe what you'd like to see.

### 💬 Discussions

For general questions, tips, or conversation, use [GitHub Discussions](https://github.com/adsamcik/meme-my-mood/discussions).

---

## FAQ

### How do I import memes?

Open the gallery, tap the **Import** button, and choose either:
- **Import Images** — pick individual images from your device
- **Import Meme Bundle** — select a `.meme.zip` file containing pre-tagged memes

### How does search work?

Riposte supports three search modes:
1. **Text search** — matches words in titles, descriptions, and text extracted from images
2. **Emoji filtering** — tap emoji chips to filter by mood/reaction
3. **Semantic search** — describe what you're looking for in natural language (requires the standard or larger build)

### Does Riposte use the internet?

No user data is ever sent over the internet. The internet permission exists only because the image loading library (Coil) includes it by default. All AI processing happens on-device.

### What are the different build flavors?

| Flavor | Description |
|--------|-------------|
| Lite | No AI models, basic text search only (~177 MB) |
| Standard | Generic AI model, full semantic search (~350 MB) |

The Standard build is recommended for most users.

### How do I change sharing settings?

Go to **Gallery → ⋮ (More options) → Settings** and scroll to the sharing section. You can configure image format, quality, and maximum size.

### Can I export my memes?

Your memes are stored as regular image files on your device. You can access them through any file manager app.

---

## Contributing

Want to contribute code? See the [Contributing Guide](https://github.com/adsamcik/meme-my-mood/blob/main/CONTRIBUTING.md) for setup instructions and guidelines.

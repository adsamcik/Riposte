# GitHub Copilot SDK API Documentation

> **Last Updated:** January 25, 2026  
> **SDK Status:** Technical Preview (v0.1.18)

## Overview

The **riposte-cli** now uses the GitHub Copilot SDK for image analysis. The SDK provides a clean Python API that communicates with the Copilot CLI via JSON-RPC.

**Key Benefits:**
- Automatic authentication via Copilot CLI
- Built-in session and lifecycle management  
- Image attachment support
- Proper error handling and streaming

## Available SDKs

| Language | Package | Install Command |
|----------|---------|-----------------|
| **Node.js / TypeScript** | `@github/copilot-sdk` | `npm install @github/copilot-sdk` |
| **Python** | `github-copilot-sdk` | `pip install github-copilot-sdk` |
| **Go** | `github.com/github/copilot-sdk/go` | `go get github.com/github/copilot-sdk/go` |
| **.NET** | `GitHub.Copilot.SDK` | `dotnet add package GitHub.Copilot.SDK` |

> ⚠️ **Note:** The old `copilot` PyPI package (v0.1.9) is an unrelated legacy project. Use `github-copilot-sdk` for the official SDK.

## Prerequisites

1. **GitHub Copilot Subscription** - Required (Free tier has limited usage)
2. **Copilot CLI Installed** - The SDK communicates with the CLI in server mode
   - Install via: https://docs.github.com/en/copilot/how-tos/set-up/install-copilot-cli
   - Verify: `copilot --version`
3. **Language Runtime:**
   - Python 3.9+
   - Node.js 18+
   - Go 1.21+
   - .NET 8.0+

## Architecture

All SDKs communicate with the Copilot CLI server via JSON-RPC:

```
Your Application
       ↓
  SDK Client
       ↓ JSON-RPC
  Copilot CLI (server mode)
```

The SDK manages the CLI process lifecycle automatically, or you can connect to an external CLI server.

---

## Python SDK Reference

### Installation

```bash
pip install github-copilot-sdk
```

### Quick Start

```python
import asyncio
from copilot import CopilotClient

async def main():
    # Create and start client
    client = CopilotClient()
    await client.start()

    # Create a session
    session = await client.create_session({"model": "gpt-5"})

    # Wait for response using session.idle event
    done = asyncio.Event()

    def on_event(event):
        if event.type.value == "assistant.message":
            print(event.data.content)
        elif event.type.value == "session.idle":
            done.set()

    session.on(on_event)

    # Send a message and wait for completion
    await session.send({"prompt": "What is 2+2?"})
    await done.wait()

    # Clean up
    await session.destroy()
    await client.stop()

asyncio.run(main())
```

### CopilotClient Options

```python
client = CopilotClient({
    "cli_path": "copilot",      # Path to CLI executable
    "cli_url": None,            # URL of existing server (e.g., "localhost:8080")
    "cwd": None,                # Working directory for CLI process
    "port": 0,                  # Server port for TCP mode (0 = random)
    "use_stdio": True,          # Use stdio transport instead of TCP
    "log_level": "info",        # Log level
    "auto_start": True,         # Auto-start server on first use
    "auto_restart": True,       # Auto-restart on crash
})
```

### Session Options

```python
session = await client.create_session({
    "model": "gpt-5",           # Model to use (gpt-5, gpt-4.1, etc.)
    "streaming": True,          # Enable streaming responses
    "tools": [...],             # Custom tools
    "system_message": {...},    # Custom system message
    "mcp_servers": {...},       # MCP server connections
    "custom_agents": [...],     # Custom agent definitions
    "infinite_sessions": {...}, # Infinite session config
})
```

### Image Support

The SDK supports image attachments via the `attachments` parameter:

```python
await session.send({
    "prompt": "What's in this image?",
    "attachments": [
        {
            "type": "file",
            "path": "/path/to/image.jpg",
        }
    ]
})
```

Supported formats: JPG, PNG, GIF, and other common image types.

You can also ask the agent to use its `view` tool:

```python
await session.send({
    "prompt": "What does the most recent jpg in this directory portray?"
})
```

### Streaming Responses

```python
import asyncio
from copilot import CopilotClient

async def main():
    client = CopilotClient()
    await client.start()

    session = await client.create_session({
        "model": "gpt-5",
        "streaming": True
    })

    done = asyncio.Event()

    def on_event(event):
        if event.type.value == "assistant.message_delta":
            # Streaming message chunk - print incrementally
            delta = event.data.delta_content or ""
            print(delta, end="", flush=True)
        elif event.type.value == "assistant.reasoning_delta":
            # Streaming reasoning chunk (if model supports reasoning)
            delta = event.data.delta_content or ""
            print(delta, end="", flush=True)
        elif event.type.value == "assistant.message":
            # Final message - complete content
            print("\n--- Final message ---")
            print(event.data.content)
        elif event.type.value == "session.idle":
            done.set()

    session.on(on_event)
    await session.send({"prompt": "Tell me a short story"})
    await done.wait()

    await session.destroy()
    await client.stop()

asyncio.run(main())
```

**Event Types for Streaming:**
- `assistant.message_delta` - Incremental text chunks
- `assistant.reasoning_delta` - Reasoning/chain-of-thought chunks (model-dependent)
- `assistant.message` - Final complete message (always sent)
- `assistant.reasoning` - Final reasoning content (always sent)
- `session.idle` - Session finished processing

### Defining Custom Tools

**Using Pydantic (Recommended):**

```python
from pydantic import BaseModel, Field
from copilot import CopilotClient, define_tool

class LookupIssueParams(BaseModel):
    id: str = Field(description="Issue identifier")

@define_tool(description="Fetch issue details from our tracker")
async def lookup_issue(params: LookupIssueParams) -> str:
    issue = await fetch_issue(params.id)
    return issue.summary

session = await client.create_session({
    "model": "gpt-5",
    "tools": [lookup_issue],
})
```

**Low-level API (Manual Schema):**

```python
from copilot import CopilotClient, Tool

async def lookup_issue(invocation):
    issue_id = invocation["arguments"]["id"]
    issue = await fetch_issue(issue_id)
    return {
        "textResultForLlm": issue.summary,
        "resultType": "success",
        "sessionLog": f"Fetched issue {issue_id}",
    }

session = await client.create_session({
    "model": "gpt-5",
    "tools": [
        Tool(
            name="lookup_issue",
            description="Fetch issue details from our tracker",
            parameters={
                "type": "object",
                "properties": {
                    "id": {"type": "string", "description": "Issue identifier"},
                },
                "required": ["id"],
            },
            handler=lookup_issue,
        )
    ],
})
```

### Infinite Sessions

Sessions automatically manage context window limits through background compaction:

```python
# Default: infinite sessions enabled
session = await client.create_session({"model": "gpt-5"})

# Access workspace path for checkpoints and files
print(session.workspace_path)
# => ~/.copilot/session-state/{session_id}/

# Custom thresholds
session = await client.create_session({
    "model": "gpt-5",
    "infinite_sessions": {
        "enabled": True,
        "background_compaction_threshold": 0.80,  # Start at 80% usage
        "buffer_exhaustion_threshold": 0.95,      # Block at 95%
    },
})

# Disable infinite sessions
session = await client.create_session({
    "model": "gpt-5",
    "infinite_sessions": {"enabled": False},
})
```

**Compaction Events:**
- `session.compaction_start` - Background compaction started
- `session.compaction_complete` - Compaction finished (includes token counts)

### Connecting to External CLI Server

Run CLI in server mode:

```bash
copilot --server --port 4321
```

Connect SDK:

```python
client = CopilotClient({
    "cli_url": "localhost:4321"
})
# Client will NOT spawn a CLI process - connects to existing server
```

---

## TypeScript/Node.js SDK Reference

### Quick Start

```typescript
import { CopilotClient } from "@github/copilot-sdk";

const client = new CopilotClient();
const session = await client.createSession({ model: "gpt-4.1" });

const response = await session.sendAndWait({ prompt: "What is 2 + 2?" });
console.log(response?.data.content);

await client.stop();
process.exit(0);
```

### Streaming Responses

```typescript
import { CopilotClient, SessionEvent } from "@github/copilot-sdk";

const client = new CopilotClient();
const session = await client.createSession({
    model: "gpt-4.1",
    streaming: true,
});

session.on((event: SessionEvent) => {
    if (event.type === "assistant.message_delta") {
        process.stdout.write(event.data.deltaContent);
    }
    if (event.type === "session.idle") {
        console.log();
    }
});

await session.sendAndWait({ prompt: "Tell me a short joke" });

await client.stop();
process.exit(0);
```

### Defining Custom Tools

```typescript
import { CopilotClient, defineTool, SessionEvent } from "@github/copilot-sdk";

const getWeather = defineTool("get_weather", {
    description: "Get the current weather for a city",
    parameters: {
        type: "object",
        properties: {
            city: { type: "string", description: "The city name" },
        },
        required: ["city"],
    },
    handler: async (args: { city: string }) => {
        const { city } = args;
        const conditions = ["sunny", "cloudy", "rainy", "partly cloudy"];
        const temp = Math.floor(Math.random() * 30) + 50;
        const condition = conditions[Math.floor(Math.random() * conditions.length)];
        return { city, temperature: `${temp}°F`, condition };
    },
});

const client = new CopilotClient();
const session = await client.createSession({
    model: "gpt-4.1",
    streaming: true,
    tools: [getWeather],
});

session.on((event: SessionEvent) => {
    if (event.type === "assistant.message_delta") {
        process.stdout.write(event.data.deltaContent);
    }
});

await session.sendAndWait({
    prompt: "What's the weather like in Seattle and Tokyo?",
});

await client.stop();
process.exit(0);
```

---

## Advanced Features

### MCP Server Connections

Connect to Model Context Protocol servers for pre-built tools:

```typescript
const session = await client.createSession({
    mcpServers: {
        github: {
            type: "http",
            url: "https://api.githubcopilot.com/mcp/",
        },
    },
});
```

### Custom Agents

Define specialized AI personas:

```typescript
const session = await client.createSession({
    customAgents: [{
        name: "pr-reviewer",
        displayName: "PR Reviewer",
        description: "Reviews pull requests for best practices",
        prompt: "You are an expert code reviewer. Focus on security, performance, and maintainability.",
    }],
});
```

### Custom System Message

```typescript
const session = await client.createSession({
    systemMessage: {
        content: "You are a helpful assistant for our engineering team. Always be concise.",
    },
});
```

---

## FAQ

### Do I need a GitHub Copilot subscription?

Yes, a GitHub Copilot subscription is required. You can use the free tier with limited usage.

### How does billing work?

Billing is based on the same model as Copilot CLI, with each prompt counting towards your premium request quota.

### Does it support BYOK (Bring Your Own Key)?

Yes, you can configure the SDK to use your own encryption keys. Refer to individual SDK documentation.

### Do I need to install the Copilot CLI separately?

Yes, the Copilot CLI must be installed separately. The SDKs communicate with the CLI in server mode.

### What tools are enabled by default?

By default, the SDK operates with the equivalent of `--allow-all`, enabling all first-party tools (file system, Git, web requests). You can customize tool availability via SDK options.

### Can I use custom agents, skills, or tools?

Yes! The SDK allows defining custom agents, skills, and tools with full flexibility.

### What models are supported?

All models available via Copilot CLI are supported. The SDK exposes a method to retrieve available models at runtime.

### Is the SDK production-ready?

The SDK is currently in **Technical Preview**. It's functional for development and testing but may not yet be suitable for production use.

---

## Rate Limiting Best Practices

Based on the January 2026 SDK documentation, the following rate limiting strategies are recommended:

### Key Principles

1. **Minimum 1-second delay** between requests (per SDK documentation)
2. **Handle multiple error types:**
   - **429** - Rate limit exceeded
   - **500/502/504** - Server errors (transient, can retry)
3. **Exponential backoff** with random jitter to prevent thundering herd
4. **Adaptive throttling** based on recent error rate

### Implementation Pattern

```python
@dataclass
class RateLimiter:
    min_delay: float = 1.0  # Per SDK docs: at least 1 second
    max_delay: float = 300.0  # 5 minute maximum
    base_backoff: float = 2.0
    jitter_factor: float = 0.25  # 25% random jitter
    
    async def wait_if_needed(self) -> None:
        elapsed = time.monotonic() - self.last_request_time
        if elapsed < self.current_delay:
            await asyncio.sleep(self.current_delay - elapsed)
        self.last_request_time = time.monotonic()
    
    def record_failure(self, is_server_error: bool = False) -> float:
        # Server errors use shorter backoff (typically transient)
        exponent = self.consecutive_failures
        if is_server_error:
            exponent = exponent * 0.5  # Half exponent for server errors
        
        base_wait = self.base_backoff ** min(exponent, 8)
        jitter = base_wait * self.jitter_factor * random.uniform(-1, 1)
        return min(base_wait + jitter, self.max_delay)
```

### Error Detection

```python
if "429" in error_message or "rate" in error_message.lower():
    # Rate limit - use standard backoff
    wait_time = rate_limiter.record_failure()

elif any(code in error_message for code in ["500", "502", "504"]):
    # Server error - use shorter backoff (transient)
    wait_time = rate_limiter.record_failure(is_server_error=True)
```

### Recommendations

| Strategy | Description |
|----------|-------------|
| **Task Queuing** | Process images one at a time, not in parallel |
| **Adaptive Throttling** | Slow down if error rate exceeds 30% |
| **Max Retries** | Up to 8 retries before giving up on an image |
| **Timeout** | 120 seconds per request maximum |

---

## Resources

- **GitHub Repository:** https://github.com/github/copilot-sdk
- **Getting Started Guide:** https://github.com/github/copilot-sdk/blob/main/docs/getting-started.md
- **Cookbook (Recipes):** https://github.com/github/copilot-sdk/blob/main/cookbook/README.md
- **Python SDK README:** https://github.com/github/copilot-sdk/blob/main/python/README.md
- **Node.js SDK README:** https://github.com/github/copilot-sdk/blob/main/nodejs/README.md
- **Go SDK README:** https://github.com/github/copilot-sdk/blob/main/go/README.md
- **.NET SDK README:** https://github.com/github/copilot-sdk/blob/main/dotnet/README.md
- **Copilot CLI Installation:** https://docs.github.com/en/copilot/how-tos/set-up/install-copilot-cli
- **GitHub Community Announcement:** https://github.com/orgs/community/discussions/184872

---

## Application to Riposte CLI

For the `riposte-cli` tool, the SDK can be used to analyze meme images:

```python
import asyncio
from copilot import CopilotClient

async def analyze_meme(image_path: str) -> dict:
    client = CopilotClient()
    await client.start()
    
    session = await client.create_session({
        "model": "gpt-5",
        "streaming": False,
    })
    
    done = asyncio.Event()
    result = {"content": ""}
    
    def on_event(event):
        if event.type.value == "assistant.message":
            result["content"] = event.data.content
        elif event.type.value == "session.idle":
            done.set()
    
    session.on(on_event)
    
    await session.send({
        "prompt": """Analyze this meme image and respond ONLY with valid JSON:
{
    "emojis": ["emoji1", "emoji2", ...],
    "title": "short title",
    "description": "brief description",
    "tags": ["tag1", "tag2", ...],
    "textContent": "any text visible in the meme"
}""",
        "attachments": [
            {"type": "file", "path": image_path}
        ]
    })
    
    await done.wait()
    await session.destroy()
    await client.stop()
    
    return json.loads(result["content"])
```

**Key Advantages:**
- Native image attachment support via `attachments`
- Automatic CLI lifecycle management
- Built-in retry and error handling
- No need to manage API endpoints or auth manually

**Requirement:** User must have Copilot CLI installed and authenticated.

# Telegram Channel Video Broadcaster

A Java 17 command-line application that downloads historical videos from a Telegram channel, normalizes them, and publishes them chronologically as one Telegram RTMPS stream. It uses TDLight's prebuilt native libraries; a C++ compiler, CMake, or native build toolchain is not required.

## One setup script for macOS and Windows

The same `scripts/setup.py` script installs/checks Java 17, Maven, FFmpeg, and FFprobe on macOS or Windows. It uses Homebrew on macOS and WinGet on Windows. The script requires Python 3.8+; it uses only Python's standard library.

- **macOS:** Install Homebrew if needed, then run `python3 scripts/setup.py`.
- **Windows:** Ensure Python 3.8+ and WinGet (App Installer) are available, then run `py -3 scripts\setup.py` in PowerShell or Command Prompt.

If a package installation updates PATH, open a new terminal and rerun the setup script to verify the tools. Maven downloads Java and prebuilt TDLight dependencies automatically on first build. No C++ compiler/toolchain is required. On Windows, the Microsoft Visual C++ Redistributable may be required as a runtime dependency; it is not a compiler.

The script installs only external tools and does not configure Telegram credentials or start broadcasting. It supports macOS Intel/Apple Silicon and Windows x86-64, as supported by the configured TDLight native packages.

## Configure and run

Create a Telegram API ID and hash at [my.telegram.org](https://my.telegram.org). Set the variables in the same terminal you use to launch Maven. Your signed-in Telegram account must have channel access.

| Variable | Required | Meaning |
|---|---:|---|
| `TELEGRAM_API_ID` | Yes | Telegram API ID |
| `TELEGRAM_API_HASH` | Yes | Telegram API hash |
| `TELEGRAM_CHANNEL` | Yes* | Channel ID, link, invite link, username, or exact title of a known chat |
| `TELEGRAM_STREAM_KEY` | Yes | RTMPS stream key only (not the URL); use a newly rotated key |
| `TELEGRAM_RTMP_BASE_URL` | No | RTMPS ingest base URL; defaults to `rtmps://dc1-1.rtmp.t.me/s/` |
| `FFMPEG_PATH` | No | FFmpeg executable or path; defaults to `ffmpeg` |
| `FFPROBE_PATH` | No | FFprobe executable or path; defaults to `ffprobe` |
| `TELEGRAM_MAX_MESSAGES` | No | Maximum history messages to scan; defaults to `100000` |
| `TELEGRAM_CHANNEL_ID` | Legacy | Used if `TELEGRAM_CHANNEL` is unset |

### macOS/Linux shell

```sh
export TELEGRAM_API_ID="your-api-id"
export TELEGRAM_API_HASH="your-api-hash"
export TELEGRAM_CHANNEL="-1001234567890"
export TELEGRAM_STREAM_KEY="your_new_stream_key"
mvn -B compile exec:java
```

### Windows PowerShell

```powershell
$env:TELEGRAM_API_ID = "your-api-id"
$env:TELEGRAM_API_HASH = "your-api-hash"
$env:TELEGRAM_CHANNEL = "-1001234567890"
$env:TELEGRAM_STREAM_KEY = "your_new_stream_key"
mvn -B compile exec:java
```

The first run asks for Telegram's login code and, when enabled, the two-step verification password. TDLib saves the authorized session locally. Do not share that session directory; it contains account authorization data.

## Selecting a channel

Set exactly one of `TELEGRAM_CHANNEL` or the legacy `TELEGRAM_CHANNEL_ID`:

- **ID:** TDLib chat ID, for example `-1001234567890`.
- **Public link/username:** `https://t.me/channelname` or `@channelname`.
- **Private invite link:** Full `https://t.me/+...` or `https://t.me/joinchat/...` invite. Supplying an invite asks TDLib to join; use only an invite you are authorized to use.
- **Name:** Exact title of a chat already known to the account. If multiple known chats share that title, use a more specific ID/link/username.

A channel ID or title alone cannot grant access to a private channel. The signed-in account must already be a member or be provided an authorized invite link. The app does not bypass channel access controls.

The app scans history once, processes matching videos oldest-to-newest, and ends the stream when the playlist finishes. It does not monitor new posts or join group video calls. All matching videos are downloaded and normalized before broadcasting, so allow enough disk space and time.

Downloads, TDLib session data, normalized files, and the playlist are stored outside the project under `%LOCALAPPDATA%\\Teleroom\\TelegramVideoBroadcaster` on Windows or `~/.local/share/telegram-video-broadcaster` elsewhere.

## Deployment and security notes

- JDK 17+, Maven 3.8+, FFmpeg, and FFprobe are required. The setup script installs the system tools; Maven resolves project libraries on the first build. An internet connection is needed for downloads and Telegram access.
- TDLight native libraries are prebuilt Maven artifacts for Windows x86-64, Linux x86-64 GNU/Linux, macOS Intel, and Apple Silicon. No native compilation or C++ toolchain is part of the build.
- FFmpeg/FFprobe are installed separately by the setup script and may be overridden with `FFMPEG_PATH` / `FFPROBE_PATH`.
- The RTMPS key must belong to a live stream Telegram is currently accepting. Never commit or share it. Revoke and rotate any key that was exposed.
- The app publishes to Telegram's live-stream ingest; it is not a Mini App player or a way to inject video into an already-running group call.

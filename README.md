# Telegram Channel Video Broadcaster

A Java 17 command-line application that downloads a channel's historical video posts, normalizes them, and publishes them in chronological order as one Telegram RTMPS stream. It uses TDLight's prebuilt TDLib native packages; **a C++ compiler, CMake, or native build toolchain is not required**.

## Requirements and quick setup

Install these runtime/build dependencies first. Maven downloads the Java and prebuilt TDLight dependencies automatically on the first run; FFmpeg is installed separately using the package manager for your operating system.

### macOS (Intel and Apple Silicon)

Install Homebrew if it is not already installed, then run:

```sh
brew install openjdk@17 maven ffmpeg
```

If macOS does not find Java after installation, follow Homebrew's `openjdk@17` instructions to add it to your `PATH`. The Maven profiles select TDLight's prebuilt `macos_amd64` (Intel) or `macos_arm64` (Apple Silicon) package based on the machine architecture. No C++ compiler or Homebrew OpenSSL symlink is required by this project. Recent macOS releases are supported when compatible with the installed Java 17 runtime and the TDLight native package; the app does not claim support for every historical macOS release.

### Windows 10/11 x86-64

Install JDK 17+, Maven 3.8+, and FFmpeg. For example, with WinGet:

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK
winget install --id Apache.Maven
winget install --id Gyan.FFmpeg.Shared
```

Open a new terminal afterward and confirm `java`, `mvn`, `ffmpeg`, and `ffprobe` are on `PATH`. Windows may require the Microsoft Visual C++ Redistributable at runtime; this is a runtime library, not a C++ compiler or build toolchain.

### Linux x86-64

Install JDK 17+, Maven 3.8+, FFmpeg, and FFprobe using your distribution's package manager. For Debian/Ubuntu:

```sh
sudo apt update && sudo apt install -y openjdk-17-jdk maven ffmpeg
```

TDLight's Linux native package is selected automatically for x86-64 GNU/Linux. Other Linux architectures are not configured.

## Configure and run

Create an API ID and hash at [my.telegram.org](https://my.telegram.org). The Telegram account used to authenticate must have access to the channel. Set the following values in the shell that will run the app:

| Variable | Required | Meaning |
|---|---:|---|
| `TELEGRAM_API_ID` | Yes | Telegram API ID |
| `TELEGRAM_API_HASH` | Yes | Telegram API hash |
| `TELEGRAM_CHANNEL` | Yes* | Channel ID, channel link, invite link, username, or exact title of a chat already known to the account |
| `TELEGRAM_STREAM_KEY` | Yes | RTMPS stream key only (not the URL); use a newly rotated key |
| `TELEGRAM_RTMP_BASE_URL` | No | RTMPS ingest base URL; defaults to `rtmps://dc1-1.rtmp.t.me/s/` |
| `FFMPEG_PATH` | No | FFmpeg executable or full path; defaults to `ffmpeg` |
| `FFPROBE_PATH` | No | FFprobe executable or full path; defaults to `ffprobe` |
| `TELEGRAM_MAX_MESSAGES` | No | Maximum history messages scanned; defaults to `100000` |
| `TELEGRAM_CHANNEL_ID` | Legacy | Accepted when `TELEGRAM_CHANNEL` is unset |

### macOS/Linux

```sh
export TELEGRAM_API_ID="your-api-id"
export TELEGRAM_API_HASH="your-api-hash"
export TELEGRAM_CHANNEL="-1001234567890"
export TELEGRAM_STREAM_KEY="your_new_stream_key"
./scripts/run.sh
```

### Windows PowerShell

```powershell
$env:TELEGRAM_API_ID = "your-api-id"
$env:TELEGRAM_API_HASH = "your-api-hash"
$env:TELEGRAM_CHANNEL = "-1001234567890"
$env:TELEGRAM_STREAM_KEY = "your_new_stream_key"
./scripts/run.ps1
```

The first run asks for Telegram's login code and, when enabled, the two-step verification password. TDLight saves the authorized session locally for later runs. **Do not share the session directory:** it contains account authorization data.

## Selecting a channel

Set exactly one of `TELEGRAM_CHANNEL` or the legacy `TELEGRAM_CHANNEL_ID`:

- **ID:** Use the TDLib chat ID, for example `-1001234567890`.
- **Public link or username:** Use `https://t.me/channelname` or `@channelname`.
- **Private invite link:** Use the full `https://t.me/+...` or `https://t.me/joinchat/...` invite URL. TDLib will join the channel if needed, so only provide an invite link when you are authorized and intend to join. Existing membership is reused.
- **Name:** Use the exact title of a chat already known to the signed-in account. If multiple known chats share that title, the app stops and asks for a more specific ID/link/username.

A channel ID or title alone cannot grant access to a private channel. The signed-in account must already be a member, or an authorized invite link must be supplied. The app does not bypass channel access controls.

The app scans history once, processes matching videos oldest-to-newest, and ends the live stream when the playlist finishes. It does not monitor new posts or join group video calls. Video posts and video documents are included; other media types are skipped. All videos are downloaded and normalized before broadcasting, so allow enough disk space and time.

Downloads, TDLib session data, normalized files, and the playlist are stored outside the project under `%LOCALAPPDATA%\\Teleroom\\TelegramVideoBroadcaster` on Windows or `~/.local/share/telegram-video-broadcaster` elsewhere.

## Deployment notes and limitations

- Prerequisites are JDK 17+, Maven 3.8+, FFmpeg, and FFprobe. `mvn compile exec:java` resolves Maven dependencies on first use; subsequent launches use the local Maven cache. A network connection is needed for the initial Maven download and Telegram operations.
- TDLight native libraries are prebuilt Maven artifacts for Windows x86-64, Linux x86-64 GNU/Linux, macOS Intel, and macOS Apple Silicon. **No C++ source compilation or C++ toolchain is part of the build.**
- FFmpeg/FFprobe are installed separately; automatic media-tool provisioning is intentionally not implemented. They can be overridden with `FFMPEG_PATH` and `FFPROBE_PATH`.
- The RTMPS key must belong to a live stream Telegram is currently accepting. Never commit it, put it in a source file, or share it. A key previously exposed should be revoked and rotated.
- The app publishes to Telegram's live-stream ingest. It is not a Mini App player or a mechanism for injecting video into an already-running group call.
- TDLib authorization, channel membership, and RTMPS ingestion remain subject to Telegram account permissions and service availability.

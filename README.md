# Stream channel videos in a Telegram Mini App

This project turns an authorized Telegram channel's video history into a private web playlist. A backend signs into Telegram using your account, downloads and converts the videos to browser-friendly MP4 files, and serves them to a simple Mini App-ready player. The player advances to the next video automatically when one ends.

This does **not** put video into an ongoing Telegram group call. The Mini App plays the playlist in its own player.

## Easiest setup: Docker + HTTPS

The included Docker Compose setup runs the backend and Caddy HTTPS server. It downloads Java/Maven dependencies and FFmpeg in the image; no Java, Maven, FFmpeg, Python, or C++ installation is needed on the host. Install Docker Desktop on Windows/macOS (or Docker Engine and Compose on a Linux server).

1. Copy `.env.example` to `.env` in this project folder.
2. Edit `.env` and set your Telegram API ID and hash from [my.telegram.org](https://my.telegram.org), your channel ID/link/name, and a domain name you control.
3. Point that domain's DNS `A` record (and, if used, `AAAA` record) to the computer/server running Docker. Allow inbound ports **80** and **443** through the firewall/router.
4. Open a terminal in this project folder and run:

   ```sh
   docker compose up --build
   ```

5. On first run, follow the Telegram prompts in the terminal: enter your phone number, login code, and two-step password if requested. Keep the terminal attached until the player server reports ready. TDLib keeps its authorized session in a persistent Docker volume for future runs.
6. Open `https://YOUR_DOMAIN` in a browser. The backend prints the private player link containing its access token in the startup log. Use that full link as your Mini App URL. Keep the tokenized link private; it grants access to the channel videos.
7. To make it launch from Telegram, configure a Mini App for your bot in BotFather and set its URL to the private HTTPS player link. The player uses Telegram's Web App JavaScript bridge when it is opened inside Telegram.

The first scan downloads every matching video, so it may take a while and require substantial disk space. Later starts reuse Telegram's saved login but scan and prepare the channel history again. Keep the Docker terminal open during initial authorization; after that, Docker Compose can keep the service running in the background with `docker compose up -d`.

### Windows and macOS notes

Install Docker Desktop, start it, then use PowerShell on Windows or Terminal on macOS. In PowerShell, copy the environment file with `Copy-Item .env.example .env`; in macOS/Linux use `cp .env.example .env`. Edit `.env` with a text editor. If Telegram authorization needs input, start with the attached `docker compose up --build` command rather than detached mode.

The Compose image targets Linux x86-64 to use TDLight's prebuilt Linux native library. Docker Desktop can run it on supported Intel and Apple Silicon machines; Apple Silicon may use emulation. No native C++ compilation is involved.

## Channel selection

`TELEGRAM_CHANNEL` accepts:

- TDLib chat ID, for example `-1001234567890`;
- public `https://t.me/channelname` link or `@channelname`;
- authorized private invite link (`https://t.me/+...` or `https://t.me/joinchat/...`); providing it asks TDLib to join;
- exact title of a chat already known to the account.

Your Telegram account must already have access, or you must provide an invite you are authorized to use. The backend does not bypass channel permissions. Use only a channel you have permission to access and stream.

## Simple controls

Open the private player link, tap **Play** once (browsers often require a user gesture), and it will automatically play the next video. Previous/Next controls are available. When it reaches the final video, it stops and says the playlist is finished. Videos are served as byte-range-enabled MP4 so browser seeking works.

The access token is generated automatically and stored in the persistent data volume at `player-token.txt`. The full tokenized player link appears in the application startup log. Anyone with that link can watch the playlist, so keep it private and use HTTPS. You may set `PLAYER_ACCESS_TOKEN` in `.env` to a custom secret at least 32 characters long. Private video files are not meant to be exposed as public unauthenticated endpoints.

## Environment settings

Required in `.env`: `TELEGRAM_API_ID`, `TELEGRAM_API_HASH`, `TELEGRAM_CHANNEL`, `PLAYER_DOMAIN`.

Optional: `PLAYER_ACCESS_TOKEN`, `TELEGRAM_MAX_MESSAGES` (default `100000`), `PLAYER_PORT` (default `8080`), `FFMPEG_PATH`, and `FFPROBE_PATH`. In Docker the application listens only inside the Compose network; Caddy provides public HTTPS on port 443.

Downloads, TDLib authorization, and processed MP4s persist in the `broadcaster-data` Docker volume. Back it up securely; it contains private account authorization and channel media. Use `docker compose down` to stop the services. Do not run `docker compose down -v` unless you intend to delete the saved session and downloaded videos.

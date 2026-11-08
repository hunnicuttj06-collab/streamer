# Telegram channel playlist for screen sharing

This app signs into Telegram with your user account, reads videos from a channel you can access, prepares browser-friendly MP4 files, and serves them through a private HTTPS player. Open the player in a normal browser, then use Telegram Desktop's built-in screen-share control to share that browser window in your meeting. The playlist advances to the next video automatically.

The app cannot programmatically join a meeting or start/control Telegram screen sharing. You start screen sharing yourself in the official Telegram client; once the browser video is playing, the playlist advances without further clicks.

## Easiest setup: Docker + HTTPS

Install Docker Desktop on Windows/macOS (or Docker Engine and Compose on a Linux server). The container installs Java/Maven dependencies and FFmpeg; you do not need to install those on the host. A C++ compiler is not required.

1. Copy `.env.example` to `.env`.
   - Windows PowerShell: `Copy-Item .env.example .env`
   - macOS/Linux: `cp .env.example .env`
2. Edit `.env` and set your domain name in `PLAYER_DOMAIN`. You can leave Telegram API ID, API hash, and channel blank; the app asks for missing values one at a time.
3. Point your domain's DNS `A` record (and `AAAA` if applicable) to the computer/server running Docker. Allow inbound ports **80** and **443** through the firewall/router.
4. Open a terminal in the project folder and run:

   ```sh
   docker compose up --build
   ```

5. If not already configured in `.env`, the app asks in order for your Telegram API ID, API hash, and channel ID/link/name. Get the API ID/hash from [my.telegram.org](https://my.telegram.org). Then follow Telegram's sign-in prompts for phone number, login code, and two-step password if requested. Keep the terminal attached for these prompts and until the player URL appears. TDLib saves the authorized session in a persistent Docker volume.
6. Open the printed HTTPS player URL in a normal browser on the computer you will use for the meeting. Keep the tokenized URL private; it gives access to the channel videos.
7. In Telegram Desktop, join or start the meeting, choose its screen-share control, and select the browser window (or the screen showing the player). Press **Play** once if the browser asks for a user gesture. The playlist continues automatically.

The first scan downloads and prepares matching videos, which can take time and disk space. Keep the Docker terminal open during first-time authorization. When set up, you can run in background with `docker compose up -d`.

### Audio and screen-sharing notes

Select the browser window in Telegram Desktop's screen-sharing dialog. If your Telegram Desktop version and operating system offer a share-audio option, enable it to include video audio; otherwise, the meeting may see the video without its sound. macOS may ask you to allow screen recording or system audio in System Settings. Test with a short video before a meeting.

Your profile is sharing its browser display through Telegram Desktop; this is not a bot joining the call. You must manually start/stop sharing using Telegram's UI. The app supplies the player and automatic playlist progression.

## Channel selection

`TELEGRAM_CHANNEL` accepts a TDLib chat ID, public `https://t.me/channelname` link or `@channelname`, authorized private invite link, or exact title of a chat already known to the account. Your account must already have access, or you must provide an invite you are authorized to use.

## Player behavior and privacy

The player has Previous/Next controls and automatically plays the following video when the current one ends. Browser autoplay policies can require one initial click on **Play**. It stops after the last video.

The app generates a private access token and stores it in the persistent data volume (`player-token.txt`). The token does not have a scheduled expiry or refresh. Anyone with the full player URL can watch the playlist, so keep it private and use HTTPS. You may set `PLAYER_ACCESS_TOKEN` in `.env` to a custom secret at least 32 characters long.

Downloads, TDLib authorization, and processed MP4s persist in the `broadcaster-data` Docker volume. Back it up securely; it contains private account authorization and channel media. Use `docker compose down` to stop services. Do not run `docker compose down -v` unless you intend to delete the saved session and downloaded videos.

## Setup prompts

If `TELEGRAM_API_ID`, `TELEGRAM_API_HASH`, or `TELEGRAM_CHANNEL` are missing, the app requests them interactively in that order, one at a time. API hash entry is hidden when run in a real terminal. Then TDLib asks for Telegram's phone number, login code, and optional two-step password as needed. For interactive first-time setup, run `docker compose up --build` attached (not `-d`) so you can answer the prompts.

Required `.env` value for HTTPS deployment: `PLAYER_DOMAIN`. Optional settings include `PLAYER_ACCESS_TOKEN`, `TELEGRAM_MAX_MESSAGES` (default `100000`), `PLAYER_PORT` (default `8080`), `FFMPEG_PATH`, and `FFPROBE_PATH`.

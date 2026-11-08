# Telegram channel playlist player

This app opens a private player for videos from a Telegram channel you can access. Telegram videos marked suitable for streaming and using MP4 are served progressively: playback can begin while TDLib continues downloading the file. Other media is downloaded and normalized with FFmpeg before playback. The player automatically moves to the next video. To show it in a meeting, share its browser window using Telegram Desktop. You start screen sharing manually; Telegram does not let this app start or control a meeting share.

## Start it

From the project folder run this command:

```powershell
python scripts\setup.py
```

The same command works in Windows PowerShell and Command Prompt. It checks for Java 17+, Maven, FFmpeg, and FFprobe, installs missing tools when possible, downloads Maven from Apache if it is missing, then starts the app. It does not rely on a WinGet Maven package. Python 3.8+ and WinGet are required on Windows; Homebrew is required on macOS. No C++ compiler is needed.

The first run may download tools and Maven libraries and can take several minutes. If WinGet installs a tool whose PATH update is not visible immediately, the setup script searches its normal install locations. If it still cannot find the tool, close and reopen the terminal and run the same command again.

## Answer the prompts

The app requests missing Telegram setup information in order, one item at a time:

1. Telegram API ID and API hash (get both from [my.telegram.org]).
2. Channel ID, link, invite link, username, or exact chat title.
3. Telegram sign-in in Telegram's phone flow: phone number, the login code Telegram requests, and the two-step password if requested. No bot-token or QR option is offered. If Telegram requires approval from another signed-in device instead of sending a code, the app will stop without displaying the approval link; that approval must be handled privately in Telegram.

Your Telegram account must already have access to the channel, or you must provide an invite link you are authorized to use. The app does not bypass channel permissions. Sign-in data is stored locally for future launches; do not share the app's data folder.

## Share the playlist in your meeting

When setup and playlist loading finish, the app prints a private player URL. Open it in a browser on the same computer and press **Play** once. For streamable MP4s, the server starts responding as soon as the requested bytes are available; other videos start after download and preparation finish. Then in Telegram Desktop start screen sharing and select the browser window. The playlist advances automatically. On macOS, allow screen recording if prompted. Turn on Telegram's share-audio option if your client/OS offers it. Test audio before a meeting.

This is browser video displayed through Telegram Desktop's normal screen-sharing feature—not a Mini App or an automatically joining profile. You must still manually start and stop screen sharing in Telegram.

## Share links with other media players or people

The app prints an authenticated M3U playlist URL that can be opened by players such as VLC or MPV, plus direct MP4 URLs for individual videos. The MP4 endpoint supports HTTP byte ranges and the generated links include the same private access token.

By default, links use `localhost` and only work on the computer running the app. To share within a LAN, set `PLAYER_PUBLIC_BASE_URL` to this computer's reachable LAN origin, for example `http://192.168.1.25:8080`. For remote sharing, use a domain with HTTPS and a reverse proxy such as the included Caddy/Compose setup; point `PLAYER_DOMAIN` DNS to the host and set `PLAYER_PUBLIC_BASE_URL=https://your-domain`. Ensure the firewall/router allows the needed traffic. Setting this value changes generated links but does not itself make the host reachable from the internet.

Anyone who receives the tokenized player, M3U, or MP4 URL can access the playlist and its videos. Share links only with trusted people; do not post them publicly. Supported players and codecs vary, although the app prepares non-progressive media as browser-friendly MP4.

The player link contains a long-lived access token. Anyone with the link can watch, so keep it private. The app token does not have a scheduled expiry or refresh. Videos and account data stay on your machine unless you deliberately expose the server.

[my.telegram.org]: https://my.telegram.org

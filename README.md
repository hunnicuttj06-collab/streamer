# Telegram channel playlist player

This app prepares videos from a Telegram channel you can access and opens a private player on your computer. The player automatically moves to the next video. To show it in a meeting, share the browser window using Telegram Desktop. **You start screen sharing manually; Telegram does not let this app start or control a meeting share.**

## Start it with one command

You need Python 3.8+ and a package manager: Homebrew on macOS, or WinGet on Windows. Then run one command from this project folder:

- **macOS:** `python3 scripts/setup.py`
- **Windows PowerShell:** `py -3 scripts\setup.py`

The script installs Java, Maven, and FFmpeg if needed, downloads the app's libraries, and starts the app. If a freshly installed program is not visible yet, close and reopen the terminal and run the same command again. Homebrew/WinGet may ask you to confirm installation.

## Answer the prompts

The app asks for missing information in order, one item at a time:

1. Telegram API ID and API hash (get both from [my.telegram.org]).
2. Channel ID, link, invite link, username, or exact chat title.
3. Telegram sign-in details (phone number, login code, and two-step password if enabled).

Your Telegram account must already have access to the channel, or you must provide an invite link you are authorized to use. The app does not bypass channel permissions. Sign-in data is stored locally for future launches; do not share the app's data folder.

## Share the playlist in your meeting

When setup and video preparation finish, the app prints a private player URL. Open it in a browser on the same computer, press **Play** once, then in Telegram Desktop start screen sharing and select the browser window. The playlist advances automatically. On macOS, allow screen recording if prompted. Turn on Telegram's share-audio option if your client/OS offers it. Test audio before a meeting.

This is a browser video displayed through Telegram Desktop's normal screen-sharing feature—not a Mini App or an automatically joining profile. You must still manually start and stop screen sharing in Telegram.

The player link contains a long-lived access token. Anyone with the link can watch, so keep it private. The app token does not have a scheduled expiry or refresh. Videos and account data stay on your machine unless you deliberately expose the server.

[my.telegram.org]: https://my.telegram.org

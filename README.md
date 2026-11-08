# Telegram channel playlist player

This app prepares videos from a Telegram channel you can access and opens a private player on your computer. The player automatically moves to the next video. To show it in a meeting, share its browser window using Telegram Desktop. You start screen sharing manually; Telegram does not let this app start or control a meeting share.

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
3. Telegram sign-in details (phone number, login code, and two-step password if enabled).

Your Telegram account must already have access to the channel, or you must provide an invite link you are authorized to use. The app does not bypass channel permissions. Sign-in data is stored locally for future launches; do not share the app's data folder.

## Share the playlist in your meeting

When setup and video preparation finish, the app prints a private player URL. Open it in a browser on the same computer, press **Play** once, then in Telegram Desktop start screen sharing and select the browser window. The playlist advances automatically. On macOS, allow screen recording if prompted. Turn on Telegram's share-audio option if your client/OS offers it. Test audio before a meeting.

This is browser video displayed through Telegram Desktop's normal screen-sharing feature—not a Mini App or an automatically joining profile. You must still manually start and stop screen sharing in Telegram.

The player link contains a long-lived access token. Anyone with the link can watch, so keep it private. The app token does not have a scheduled expiry or refresh. Videos and account data stay on your machine unless you deliberately expose the server.

[my.telegram.org]: https://my.telegram.org

#!/usr/bin/env python3
"""Install the broadcaster's external tools on macOS or Windows."""

import platform
import shutil
import subprocess
import sys


MAC_PACKAGES = [
    ("java", "openjdk@17"),
    ("mvn", "maven"),
    ("ffmpeg", "ffmpeg"),
    ("ffprobe", "ffmpeg"),
]
WINDOWS_PACKAGES = [
    ("java", "EclipseAdoptium.Temurin.17.JDK"),
    ("mvn", "Apache.Maven"),
    ("ffmpeg", "Gyan.FFmpeg.Shared"),
    ("ffprobe", "Gyan.FFmpeg.Shared"),
]


def run(command):
    print("+", " ".join(command), flush=True)
    subprocess.run(command, check=True)


def main():
    if sys.version_info < (3, 8):
        sys.exit("Python 3.8 or newer is required to run this setup script.")

    system = platform.system()
    if system == "Darwin":
        if not shutil.which("brew"):
            sys.exit("Homebrew is required. Install it from https://brew.sh, then rerun this script.")
        missing = sorted({package for command, package in MAC_PACKAGES
                          if not shutil.which(command)})
        if missing:
            run(["brew", "install", *missing])
    elif system == "Windows":
        winget = shutil.which("winget")
        if not winget:
            sys.exit("WinGet is required. Install/update 'App Installer' from Microsoft Store, then rerun.")
        missing = []
        installed_packages = set()
        for command, package in WINDOWS_PACKAGES:
            if not shutil.which(command) and package not in installed_packages:
                missing.append(package)
                installed_packages.add(package)
        for package in missing:
            run([winget, "install", "--id", package,
                 "--accept-source-agreements", "--accept-package-agreements"])
    else:
        sys.exit("This one-script installer supports macOS and Windows only.")

    absent = [name for name in ("java", "mvn", "ffmpeg", "ffprobe")
              if not shutil.which(name)]
    if absent:
        print("Installed dependencies may not yet be on this terminal's PATH: "
              + ", ".join(absent))
        print("Open a new terminal, then rerun this setup script to verify installations.")
        return 1

    print("Java, Maven, FFmpeg, and FFprobe are ready.")
    print("Next, set TELEGRAM_API_ID, TELEGRAM_API_HASH, TELEGRAM_CHANNEL, and "
          "TELEGRAM_STREAM_KEY, then run the project with Maven.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

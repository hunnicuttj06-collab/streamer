#!/usr/bin/env python3
"""One-command setup and launch for the Telegram channel playlist player."""

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


def install_dependencies():
    system = platform.system()
    if system == "Darwin":
        brew = shutil.which("brew")
        if not brew:
            sys.exit("Homebrew is not installed. Install it once from https://brew.sh, then rerun this script.")
        missing = sorted({package for tool, package in MAC_PACKAGES if not shutil.which(tool)})
        if missing:
            run([brew, "install", *missing])
    elif system == "Windows":
        winget = shutil.which("winget")
        if not winget:
            sys.exit("WinGet is unavailable. Install/update App Installer, then rerun this script.")
        missing = []
        installed = set()
        for tool, package in WINDOWS_PACKAGES:
            if not shutil.which(tool) and package not in installed:
                missing.append(package)
                installed.add(package)
        for package in missing:
            run([winget, "install", "--id", package,
                 "--accept-source-agreements", "--accept-package-agreements"])
    else:
        sys.exit("This one-command setup supports macOS and Windows.")

    missing_tools = [tool for tool in ("java", "mvn", "ffmpeg", "ffprobe")
                     if not shutil.which(tool)]
    if missing_tools:
        sys.exit("Some installed tools are not visible in this terminal yet: "
                 + ", ".join(missing_tools)
                 + ". Close and reopen the terminal, then run this same command again.")


def main():
    if sys.version_info < (3, 8):
        sys.exit("Python 3.8 or newer is required.")

    install_dependencies()
    project_root = __import__("pathlib").Path(__file__).resolve().parent.parent
    print("\nStarting setup. The app will ask for any missing Telegram details one at a time.")
    print("After the player opens, start screen sharing manually in Telegram Desktop.\n")
    return subprocess.call(["mvn", "-B", "compile", "exec:java"], cwd=project_root)


if __name__ == "__main__":
    raise SystemExit(main())

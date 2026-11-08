#!/usr/bin/env python3
"""One-command setup and launch for the Telegram channel playlist player."""

import os
import platform
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path


MAC_PACKAGES = [
    ("java", "openjdk@17"),
    ("mvn", "maven"),
    ("ffmpeg", "ffmpeg"),
]
WINDOWS_PACKAGES = [
    ("java", "EclipseAdoptium.Temurin.17.JDK"),
    ("ffmpeg", "Gyan.FFmpeg.Shared"),
]
MAVEN_VERSION = "3.9.11"
MAVEN_HOME_NAME = f"apache-maven-{MAVEN_VERSION}"


def run(command):
    print("+", " ".join(str(part) for part in command), flush=True)
    subprocess.run(command, check=True)


def windows_maven_candidates():
    candidates = []
    configured = os.environ.get("MAVEN_HOME") or os.environ.get("M2_HOME")
    if configured:
        candidates.append(Path(configured))
    for entry in os.environ.get("PATH", "").split(os.pathsep):
        if entry:
            path = Path(entry.strip('"'))
            if path.name.lower() == "bin":
                candidates.append(path.parent)
    local_app_data = Path(os.environ.get("LOCALAPPDATA", Path.home()))
    program_files = Path(os.environ.get("ProgramFiles", r"C:\Program Files"))
    candidates.extend([
        local_app_data / "streamer" / "tools" / MAVEN_HOME_NAME,
        local_app_data / "Programs" / MAVEN_HOME_NAME,
        program_files / "Apache" / "Maven",
        Path(r"C:\tools\apache-maven"),
    ])
    # Also locate any Maven version already unpacked under the app's local tools folder.
    tools = local_app_data / "streamer" / "tools"
    if tools.is_dir():
        candidates.extend(sorted(tools.glob("apache-maven-*"), reverse=True))
    seen = set()
    for home in candidates:
        try:
            canonical = home.expanduser().resolve()
        except OSError:
            continue
        if canonical not in seen:
            seen.add(canonical)
            yield canonical


def find_windows_maven():
    command = shutil.which("mvn")
    if command:
        return command
    for home in windows_maven_candidates():
        for name in ("mvn.cmd", "mvn.bat", "mvn.exe"):
            candidate = home / "bin" / name
            if candidate.is_file():
                return str(candidate)
    return None


def install_maven_windows():
    """Find Maven or install the official Apache binary ZIP under LocalAppData."""
    existing = find_windows_maven()
    if existing:
        bin_directory = str(Path(existing).parent)
        os.environ["PATH"] = bin_directory + os.pathsep + os.environ.get("PATH", "")
        print(f"Using Maven at {existing}")
        return existing

    local_app_data = Path(os.environ.get("LOCALAPPDATA", Path.home()))
    install_root = local_app_data / "streamer" / "tools"
    maven_home = install_root / MAVEN_HOME_NAME
    maven_bin = maven_home / "bin"
    executable = maven_bin / "mvn.cmd"
    # Apache Maven's official download layout includes the maven-3 directory.
    url = (f"https://dlcdn.apache.org/maven/maven-3/{MAVEN_VERSION}/binaries/"
           f"{MAVEN_HOME_NAME}-bin.zip")

    install_root.mkdir(parents=True, exist_ok=True)
    print(f"Maven was not found. Downloading Apache Maven {MAVEN_VERSION}...", flush=True)
    with tempfile.TemporaryDirectory(prefix="streamer-maven-") as temporary:
        archive = Path(temporary) / "maven.zip"
        urllib.request.urlretrieve(url, archive)
        with zipfile.ZipFile(archive) as bundle:
            base = Path(temporary).resolve()
            for item in bundle.infolist():
                destination = (base / item.filename).resolve()
                if destination != base and base not in destination.parents:
                    raise RuntimeError("The Maven archive contains an unsafe path.")
            bundle.extractall(base)
        extracted = base / MAVEN_HOME_NAME
        if not (extracted / "bin" / "mvn.cmd").is_file():
            raise RuntimeError("The Apache Maven download did not contain bin\\mvn.cmd.")
        if maven_home.exists():
            shutil.rmtree(maven_home)
        shutil.move(str(extracted), str(maven_home))

    os.environ["MAVEN_HOME"] = str(maven_home)
    os.environ["PATH"] = str(maven_bin) + os.pathsep + os.environ.get("PATH", "")
    print(f"Maven installed at {maven_home}")
    return str(executable)


def install_dependencies():
    system = platform.system()
    if system == "Darwin":
        brew = shutil.which("brew")
        if not brew:
            sys.exit("Homebrew is not installed. Install it once from https://brew.sh, then rerun this script.")
        missing = sorted({package for tool, package in MAC_PACKAGES if not shutil.which(tool)})
        if missing:
            run([brew, "install", *missing])
        maven = shutil.which("mvn")
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
            try:
                run([winget, "install", "--id", package,
                     "--accept-source-agreements", "--accept-package-agreements"])
            except subprocess.CalledProcessError as error:
                sys.exit(f"WinGet could not install {package} (exit code {error.returncode}). "
                         "Install it manually, reopen the terminal, and run setup again.")
        # WinGet may have installed tools without updating this already-running process's PATH.
        if not shutil.which("java") or not shutil.which("ffmpeg"):
            sys.exit("Java or FFmpeg was installed but is not on this terminal's PATH yet. "
                     "Close and reopen the terminal, then run `python scripts\\setup.py` again.")
        maven = install_maven_windows()
    else:
        sys.exit("This one-command setup supports macOS and Windows.")

    missing_tools = [tool for tool in ("java", "ffmpeg", "ffprobe") if not shutil.which(tool)]
    if not maven:
        missing_tools.append("mvn")
    if missing_tools:
        sys.exit("Required tools could not be found: " + ", ".join(missing_tools)
                 + ". Reopen the terminal and run `python scripts\\setup.py` again.")
    return maven


def main():
    if sys.version_info < (3, 8):
        sys.exit("Python 3.8 or newer is required.")

    maven = install_dependencies()
    project_root = Path(__file__).resolve().parent.parent
    print("\nStarting setup. The app will ask for any missing Telegram details one at a time.")
    print("After the player opens, start screen sharing manually in Telegram Desktop.\n")
    return subprocess.call([maven, "-B", "compile", "exec:java"], cwd=project_root)


if __name__ == "__main__":
    raise SystemExit(main())

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


def run(command, check=True):
    print("+", " ".join(str(part) for part in command), flush=True)
    return subprocess.run(command, check=check)


def refresh_windows_path():
    """Merge PATH values from the Windows registry into this Python process."""
    if platform.system() != "Windows":
        return
    try:
        import winreg
        paths = [os.environ.get("PATH", "")]
        locations = [
            (winreg.HKEY_CURRENT_USER, r"Environment"),
            (winreg.HKEY_LOCAL_MACHINE,
             r"SYSTEM\CurrentControlSet\Control\Session Manager\Environment"),
        ]
        for hive, key in locations:
            try:
                with winreg.OpenKey(hive, key) as registry_key:
                    value, _ = winreg.QueryValueEx(registry_key, "Path")
                    paths.append(os.path.expandvars(value))
            except OSError:
                pass
        combined = []
        seen = set()
        for value in paths:
            for item in value.split(os.pathsep):
                item = item.strip().strip('"')
                if item and item.casefold() not in seen:
                    combined.append(item)
                    seen.add(item.casefold())
        os.environ["PATH"] = os.pathsep.join(combined)
    except ImportError:
        pass


def windows_tool_candidates(tool):
    """Check normal PATH and common package-manager install locations."""
    refresh_windows_path()
    found = shutil.which(tool)
    if found:
        return found

    suffixes = (".exe", ".cmd", ".bat")
    names = [tool + suffix for suffix in suffixes]
    local = Path(os.environ.get("LOCALAPPDATA", Path.home()))
    program_files = Path(os.environ.get("ProgramFiles", r"C:\Program Files"))
    roots = []
    if tool == "java":
        roots.extend([program_files / "Eclipse Adoptium", program_files / "Java",
                      local / "Programs", local / "Microsoft" / "WinGet" / "Packages"])
    elif tool in ("ffmpeg", "ffprobe"):
        roots.extend([program_files / "FFmpeg", local / "Programs" / "ffmpeg",
                      local / "Microsoft" / "WinGet" / "Packages"])

    for root in roots:
        if not root.is_dir():
            continue
        # Walk only known directories and stop at the executable to cover versioned package layouts.
        try:
            for name in names:
                matches = root.glob(f"**/{name}")
                candidate = next(matches, None)
                if candidate and candidate.is_file():
                    os.environ["PATH"] = str(candidate.parent) + os.pathsep + os.environ.get("PATH", "")
                    return str(candidate)
        except (OSError, PermissionError):
            continue
    return None


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
    local = Path(os.environ.get("LOCALAPPDATA", Path.home()))
    program_files = Path(os.environ.get("ProgramFiles", r"C:\Program Files"))
    candidates.extend([
        local / "streamer" / "tools" / MAVEN_HOME_NAME,
        local / "Programs" / MAVEN_HOME_NAME,
        program_files / "Apache" / "Maven",
        Path(r"C:\tools\apache-maven"),
    ])
    tools = local / "streamer" / "tools"
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
    existing = find_windows_maven()
    if existing:
        os.environ["PATH"] = str(Path(existing).parent) + os.pathsep + os.environ.get("PATH", "")
        print(f"Using Maven at {existing}")
        return existing

    local = Path(os.environ.get("LOCALAPPDATA", Path.home()))
    install_root = local / "streamer" / "tools"
    maven_home = install_root / MAVEN_HOME_NAME
    executable = maven_home / "bin" / "mvn.cmd"
    url = (f"https://archive.apache.org/dist/maven/maven-3/{MAVEN_VERSION}/binaries/"
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
            raise RuntimeError("The Apache Maven archive did not contain bin\\mvn.cmd.")
        if maven_home.exists():
            shutil.rmtree(maven_home)
        shutil.move(str(extracted), str(maven_home))

    os.environ["MAVEN_HOME"] = str(maven_home)
    os.environ["PATH"] = str(executable.parent) + os.pathsep + os.environ.get("PATH", "")
    print(f"Maven installed at {maven_home}")
    return str(executable)


def install_windows_tool(tool, package_id):
    if windows_tool_candidates(tool):
        return
    winget = shutil.which("winget")
    if not winget:
        sys.exit("WinGet is unavailable. Install/update App Installer, then run `python scripts\\setup.py` again.")

    result = run([winget, "install", "--id", package_id,
                  "--accept-source-agreements", "--accept-package-agreements"], check=False)
    refresh_windows_path()
    if windows_tool_candidates(tool):
        return

    # WinGet can return a non-zero 'already installed/no upgrade' result. Re-check locations
    # before treating it as a true installation failure.
    if result.returncode != 0:
        sys.exit(f"WinGet could not find a usable {tool} installation (exit code {result.returncode}). "
                 "Check the install output above, then reopen PowerShell and run `python scripts\\setup.py` again.")
    sys.exit(f"WinGet completed for {package_id}, but {tool} is not available yet. "
             "Close and reopen PowerShell, then run `python scripts\\setup.py` again.")


def install_dependencies():
    system = platform.system()
    if system == "Darwin":
        brew = shutil.which("brew")
        if not brew:
            sys.exit("Homebrew is not installed. Install it once from https://brew.sh, then rerun the setup command.")
        missing = sorted({package for tool, package in MAC_PACKAGES if not shutil.which(tool)})
        if missing:
            run([brew, "install", *missing])
        maven = shutil.which("mvn")
    elif system == "Windows":
        for tool, package in WINDOWS_PACKAGES:
            install_windows_tool(tool, package)
        maven = install_maven_windows()
    else:
        sys.exit("This one-command setup supports macOS and Windows.")

    if system == "Windows":
        missing = [tool for tool in ("java", "ffmpeg", "ffprobe")
                   if not windows_tool_candidates(tool)]
    else:
        missing = [tool for tool in ("java", "ffmpeg", "ffprobe") if not shutil.which(tool)]
    if not maven:
        missing.append("mvn")
    if missing:
        sys.exit("Required tools could not be found: " + ", ".join(missing)
                 + ". Run `python scripts\\setup.py` again after installing them.")
    return maven


def main():
    if sys.version_info < (3, 8):
        sys.exit("Python 3.8 or newer is required.")
    maven = install_dependencies()
    project_root = Path(__file__).resolve().parent.parent
    print("\nStarting setup. The app will ask for any missing Telegram details one at a time.")
    print("After the player opens, start screen sharing manually in Telegram Desktop.\n")
    command = [maven, "-B", "compile", "exec:java"]
    if platform.system() == "Windows" and Path(maven).suffix.lower() in (".cmd", ".bat"):
        command = [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/s", "/c", "\"" + " ".join(command) + "\""]
    return subprocess.call(command, cwd=project_root)


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Install/check prerequisites and launch the Telegram playlist player."""

import hashlib
import os
import platform
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

MAVEN_VERSION = "3.9.11"
MAVEN_DIR = f"apache-maven-{MAVEN_VERSION}"
WINDOWS_PACKAGES = {
    "java": "EclipseAdoptium.Temurin.17.JDK",
    "ffmpeg": "Gyan.FFmpeg.Shared",
}


def run(command, *, check=True, capture=False, timeout=None):
    printable = " ".join(str(part) for part in command)
    print("+", printable, flush=True)
    return subprocess.run(
        command,
        check=check,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.STDOUT if capture else None,
        timeout=timeout,
    )


def prepend_path(directory):
    directory = str(directory)
    entries = os.environ.get("PATH", "").split(os.pathsep)
    if not any(item.casefold() == directory.casefold() for item in entries):
        os.environ["PATH"] = directory + os.pathsep + os.environ.get("PATH", "")


def refresh_windows_path():
    """Refresh PATH in this process after WinGet changes the user/machine PATH."""
    if platform.system() != "Windows":
        return
    try:
        import winreg
        values = [os.environ.get("PATH", "")]
        for hive, key in (
            (winreg.HKEY_CURRENT_USER, r"Environment"),
            (winreg.HKEY_LOCAL_MACHINE,
             r"SYSTEM\CurrentControlSet\Control\Session Manager\Environment"),
        ):
            try:
                with winreg.OpenKey(hive, key) as registry_key:
                    value, _ = winreg.QueryValueEx(registry_key, "Path")
                    values.append(os.path.expandvars(value))
            except OSError:
                continue
        combined, seen = [], set()
        for value in values:
            for entry in value.split(os.pathsep):
                entry = entry.strip().strip('"')
                if entry and entry.casefold() not in seen:
                    seen.add(entry.casefold())
                    combined.append(entry)
        os.environ["PATH"] = os.pathsep.join(combined)
    except ImportError:
        return


def local_app_data():
    fallback = Path.home() / ".local"
    if platform.system() == "Windows":
        os.environ["LOCALAPPDATA"] = str(fallback)
        return fallback
    value = Path(os.environ.get("LOCALAPPDATA") or fallback)
    os.environ["LOCALAPPDATA"] = str(value)
    return value


def candidate_executables(tool):
    """Check standard install locations without crawling all of Program Files."""
    refresh_windows_path()
    found = shutil.which(tool)
    if found:
        yield Path(found)

    program_files = Path(os.environ.get("ProgramFiles", r"C:\Program Files"))
    roots = []
    if tool == "java":
        roots.extend([
            program_files / "Eclipse Adoptium",
            program_files / "Java",
            local_app_data() / "Programs" / "Eclipse Adoptium",
        ])
        package_root = local_app_data() / "Microsoft" / "WinGet" / "Packages"
        roots.extend(package_root.glob("EclipseAdoptium.Temurin.17.JDK_*"))
        names = ("java.exe",)
    else:
        roots.extend([
            Path(r"C:\ffmpeg\bin"),
            program_files / "ffmpeg" / "bin",
            local_app_data() / "Programs" / "ffmpeg" / "bin",
        ])
        package_root = local_app_data() / "Microsoft" / "WinGet" / "Packages"
        roots.extend(package_root.glob("Gyan.FFmpeg.Shared_*"))
        names = ("ffmpeg.exe", "ffprobe.exe")

    yielded = set()
    for root in roots:
        if not root.exists():
            continue
        direct = [root / name for name in names]
        direct.extend(root / "bin" / name for name in names)
        for candidate in direct:
            if candidate.is_file() and str(candidate).casefold() not in yielded:
                yielded.add(str(candidate).casefold())
                yield candidate
        # WinGet installs may include versioned subdirectories; search only that package root.
        if root.name.startswith(("Eclipse Adoptium", "Java", "EclipseAdoptium.", "Gyan.")):
            for name in names:
                try:
                    for candidate in root.glob(f"**/{name}"):
                        if candidate.is_file() and str(candidate).casefold() not in yielded:
                            yielded.add(str(candidate).casefold())
                            yield candidate
                except (OSError, PermissionError):
                    continue


def resolve_windows_tool(tool):
    for candidate in candidate_executables(tool):
        prepend_path(candidate.parent)
        return str(candidate)
    return None


def java_version(java_command):
    try:
        result = run([java_command, "-version"], check=False, capture=True, timeout=20)
    except (OSError, subprocess.TimeoutExpired):
        return None
    output = result.stdout or ""
    match = re.search(r'version\s+"?([0-9]+)(?:\.([0-9]+))?', output, re.IGNORECASE)
    if not match:
        match = re.search(r"openjdk\s+([0-9]+)(?:\.([0-9]+))?", output, re.IGNORECASE)
    return int(match.group(1)) if match else None


def valid_java(java_command):
    version = java_version(java_command)
    if version is not None and version >= 17:
        print(f"Using Java {version}: {java_command}")
        return True
    if version is not None:
        print(f"Found Java {version}; this project needs Java 17 or newer.")
    return False


def install_windows_package(tool, package_id):
    current = resolve_windows_tool(tool)
    if tool == "java" and current and valid_java(current):
        return current
    if tool != "java" and current:
        return current

    winget = shutil.which("winget")
    if not winget:
        raise RuntimeError("WinGet is unavailable. Install/update App Installer, then rerun this script.")

    # Do not let WinGet's nonzero 'already installed/no upgrade' result abort setup.
    result = run([
        winget, "install", "--id", package_id, "--exact", "--source", "winget",
        "--accept-source-agreements", "--accept-package-agreements",
    ], check=False)
    refresh_windows_path()
    current = resolve_windows_tool(tool)
    if current and (tool != "java" or valid_java(current)):
        return current

    if result.returncode != 0:
        print(f"WinGet returned {result.returncode} while handling {package_id}; "
              "checking known install paths did not find a usable tool.")
    raise RuntimeError(
        f"Could not find a usable {tool} after the WinGet install attempt. "
        "Check the WinGet output above, then reopen PowerShell and run `python scripts\\setup.py` again."
    )


def find_maven_windows():
    command = shutil.which("mvn")
    if command:
        return command

    homes = []
    configured = os.environ.get("MAVEN_HOME") or os.environ.get("M2_HOME")
    if configured:
        homes.append(Path(configured))
    for entry in os.environ.get("PATH", "").split(os.pathsep):
        if entry:
            candidate = Path(entry.strip('"'))
            if candidate.name.casefold() == "bin":
                homes.append(candidate.parent)
    local = local_app_data()
    homes.extend([
        local / "streamer" / "tools" / MAVEN_DIR,
        local / "Programs" / MAVEN_DIR,
        Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "Apache" / "Maven",
        Path(r"C:\tools\apache-maven"),
    ])
    tools = local / "streamer" / "tools"
    if tools.is_dir():
        homes.extend(sorted(tools.glob("apache-maven-*"), reverse=True))

    seen = set()
    for home in homes:
        try:
            home = home.expanduser()
            normalized = str(home).lower()
            if "\\packages\\" in normalized or "\\localcache\\local" in normalized:
                continue
            home = home.absolute()
        except (OSError, RuntimeError):
            continue
        if str(home) in seen:
            continue
        seen.add(str(home))
        for name in ("mvn.cmd", "mvn.bat", "mvn.exe"):
            executable = home / "bin" / name
            try:
                if executable.is_file():
                    prepend_path(executable.parent)
                    return str(executable)
            except (OSError, PermissionError):
                continue
    return None


def download_file(url, destination):
    request = urllib.request.Request(url, headers={"User-Agent": "streamer-setup/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response, destination.open("wb") as output:
        shutil.copyfileobj(response, output)


def install_maven_windows():
    existing = find_maven_windows()
    if existing:
        print(f"Using Maven at {existing}")
        return existing

    install_root = local_app_data() / "streamer" / "tools"
    install_root.mkdir(parents=True, exist_ok=True)
    target = install_root / MAVEN_DIR
    base_url = (f"https://archive.apache.org/dist/maven/maven-3/{MAVEN_VERSION}/binaries/"
                f"{MAVEN_DIR}-bin.zip")
    print(f"Maven was not found. Downloading Apache Maven {MAVEN_VERSION}...", flush=True)

    with tempfile.TemporaryDirectory(prefix="streamer-maven-") as temporary_name:
        temporary = Path(temporary_name)
        archive = temporary / "maven.zip"
        checksum_file = temporary / "maven.zip.sha512"
        download_file(base_url, archive)
        download_file(base_url + ".sha512", checksum_file)
        expected = re.search(r"\b([0-9a-fA-F]{128})\b", checksum_file.read_text(
            encoding="ascii", errors="replace"))
        if not expected:
            raise RuntimeError("Apache Maven checksum file was missing or unreadable; refusing an unverified download.")
        digest = hashlib.sha512(archive.read_bytes()).hexdigest()
        if digest.lower() != expected.group(1).lower():
            raise RuntimeError("Apache Maven download failed SHA-512 verification; refusing to install it.")

        extracted_root = temporary / "unpacked"
        extracted_root.mkdir()
        with zipfile.ZipFile(archive) as bundle:
            root = extracted_root.resolve()
            for item in bundle.infolist():
                destination = (root / item.filename).resolve()
                if destination != root and root not in destination.parents:
                    raise RuntimeError("The Maven archive contains an unsafe path.")
            bundle.extractall(root)
        extracted = extracted_root / MAVEN_DIR
        if not (extracted / "bin" / "mvn.cmd").is_file():
            raise RuntimeError("The verified Apache Maven archive did not contain bin\\mvn.cmd.")

        replacement = install_root / (MAVEN_DIR + ".new")
        if replacement.exists():
            shutil.rmtree(replacement)
        shutil.move(str(extracted), str(replacement))
        if target.exists():
            shutil.rmtree(target)
        replacement.replace(target)

    executable = target / "bin" / "mvn.cmd"
    os.environ["MAVEN_HOME"] = str(target)
    prepend_path(executable.parent)
    print(f"Maven installed at {target}")
    return str(executable)


def install_mac_dependencies():
    brew = shutil.which("brew")
    if not brew:
        raise RuntimeError("Homebrew is required. Install it from https://brew.sh, then rerun this script.")
    for tool, package in (("java", "openjdk@17"), ("mvn", "maven"),
                          ("ffmpeg", "ffmpeg")):
        if tool == "java":
            found = shutil.which("java")
            if found and valid_java(found):
                continue
        elif shutil.which(tool):
            continue
        run([brew, "install", package])
    java_prefix = run([brew, "--prefix", "openjdk@17"], capture=True, timeout=30).stdout.strip()
    if java_prefix:
        prepend_path(Path(java_prefix) / "bin")
    return shutil.which("mvn")


def install_dependencies():
    system = platform.system()
    if system == "Windows":
        install_windows_package("java", WINDOWS_PACKAGES["java"])
        install_windows_package("ffmpeg", WINDOWS_PACKAGES["ffmpeg"])
        maven = install_maven_windows()
        java = resolve_windows_tool("java")
        ffmpeg = resolve_windows_tool("ffmpeg")
        ffprobe = resolve_windows_tool("ffprobe")
        if not java or not valid_java(java):
            raise RuntimeError("Java 17 or newer could not be located after setup.")
        if not ffmpeg or not ffprobe:
            raise RuntimeError("FFmpeg/FFprobe could not be located. Install the FFmpeg package, then retry.")
        return maven
    if system == "Darwin":
        maven = install_mac_dependencies()
        missing = [tool for tool in ("java", "mvn", "ffmpeg", "ffprobe") if not shutil.which(tool)]
        java = shutil.which("java")
        if java and not valid_java(java):
            missing.append("Java 17+")
        if missing:
            raise RuntimeError("Required tools are still unavailable: " + ", ".join(missing))
        return maven
    raise RuntimeError("This setup script supports macOS and Windows.")


def launch(maven, project_root):
    args = ["-B", "compile", "exec:java"]
    if platform.system() == "Windows" and Path(maven).suffix.lower() in (".cmd", ".bat"):
        # install_maven_windows() ensures the executable's directory is on PATH.
        # Resolve it by name so cmd doesn't re-escape the quoted full path.
        return subprocess.call("mvn " + " ".join(args), shell=True, cwd=project_root)
    return subprocess.call([maven, *args], cwd=project_root)


def main():
    if sys.version_info < (3, 8):
        print("Python 3.8 or newer is required.", file=sys.stderr)
        return 1
    try:
        maven = install_dependencies()
    except (RuntimeError, OSError, urllib.error.URLError, zipfile.BadZipFile,
            subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        print(f"Setup failed: {error}", file=sys.stderr)
        return 1

    project_root = Path(__file__).resolve().parent.parent
    print("\nStarting the player. Missing Telegram details will be requested one at a time.")
    print("When the player opens, share its browser window manually in Telegram Desktop.\n")
    try:
        return launch(maven, project_root)
    except (OSError, subprocess.TimeoutExpired) as error:
        print(f"Could not start Maven: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

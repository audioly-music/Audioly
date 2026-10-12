"""Validate or advance Audioly's decimal version sequence."""
import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def parse_version(value):
    if not re.fullmatch(r"[1-9][0-9]*\.[0-9]\.[0-9]", value):
        raise ValueError("Use major.minor.patch; minor and patch must each be 0–9.")
    return tuple(map(int, value.split(".")))


def next_version(value, part="patch"):
    major, minor, patch = parse_version(value)
    if part == "major":
        return f"{major + 1}.0.0"
    if part == "minor":
        patch = 0
        minor += 1
    else:
        patch += 1
        if patch == 10:
            minor, patch = minor + 1, 0
    if minor == 10:
        major, minor = major + 1, 0
    return f"{major}.{minor}.{patch}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("show", "validate", "bump"))
    parser.add_argument("--part", choices=("patch", "minor", "major"), default="patch")
    parser.add_argument("--tag")
    args = parser.parse_args()
    path = ROOT / "version.properties"
    values = dict(line.split("=", 1) for line in path.read_text().splitlines() if "=" in line)
    version = values["versionName"]
    parse_version(version)
    code = int(values["versionCode"])
    if not 1 <= code <= 2_100_000_000:
        raise ValueError("Invalid Android versionCode")
    if args.tag and args.tag != "v" + version:
        raise ValueError(f"Release tag must be v{version}, matching version.properties")
    if args.command == "bump":
        version = next_version(version, args.part)
        if code == 2_100_000_000:
            raise ValueError("Android versionCode is exhausted")
        path.write_text(f"versionName={version}\nversionCode={code + 1}\n", encoding="utf-8")
    print(version)


if __name__ == "__main__":
    main()

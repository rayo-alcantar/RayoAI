"""Android Release tooling for RayoAI. No UI imports, Git pushes, local cleanup or implicit builds."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PUBLIC_REPO = "rayo-alcantar/rayoai_for_android"
TAG_PATTERN = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)-(stable|beta)")
SHA_PATTERN = re.compile(r"[0-9a-f]{40}")
APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"


def parse_tag(tag: str) -> dict:
    match = TAG_PATTERN.fullmatch(tag)
    if not match:
        raise ValueError("Use an exact X.Y.Z-stable or X.Y.Z-beta tag (no v prefix).")
    version = ".".join(match.groups()[:3])
    channel = match[4]
    return {
        "tag": tag,
        "version": version,
        "channel": channel,
        "apk": "app-github-release.apk",
    }


def run(*args: str) -> str:
    result = subprocess.run(args, cwd=ROOT, text=True, capture_output=True)
    if result.returncode:
        # Do not include environment values (in particular GH_TOKEN) in errors.
        raise RuntimeError(f"{args[0]} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def extract_gradle_versions(root: Path = ROOT) -> tuple[str, int]:
    gradle_file = root / "app/build.gradle.kts"
    if not gradle_file.exists():
        raise ValueError(f"Build file not found: {gradle_file}")
    content = gradle_file.read_text(encoding="utf-8")
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    code_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    if not name_match or not code_match:
        raise ValueError("Could not find versionName or versionCode in app/build.gradle.kts")
    return name_match.group(1), int(code_match.group(1))


def metadata(tag: str, root: Path = ROOT) -> dict:
    data = parse_tag(tag)
    version_name, version_code = extract_gradle_versions(root)
    if version_name != data["version"]:
        raise ValueError(
            f"Tag version ({data['version']}) and app/build.gradle.kts versionName ({version_name}) must agree."
        )
    data["version_code"] = version_code

    notes_file = root / "release-notes.txt"
    if not notes_file.exists():
        raise ValueError("release-notes.txt file not found.")
    notes = notes_file.read_text(encoding="utf-8").strip()
    if not notes:
        raise ValueError("release-notes.txt is empty.")
    first_line = notes.splitlines()[0]
    if not re.search(rf"(?<![\d.]){re.escape(data['version'])}(?!\d|\.\d)", first_line):
        raise ValueError(
            f"release-notes.txt first line must mention version {data['version']} (got: '{first_line}')."
        )

    check_strings(root)
    return data


def validate(tag: str, *, build_only: bool = False) -> None:
    data = metadata(tag)
    sha = run("git", "rev-parse", "HEAD")
    if not build_only:
        tag_commit = run("git", "rev-parse", f"refs/tags/{tag}^{{commit}}")
        if tag_commit != sha:
            raise ValueError(
                f"Checkout commit ({sha}) does not match source tag ({tag}) commit ({tag_commit})."
            )
    allowed = False
    branches = ("dev", "main", "master")
    for branch in branches:
        result = subprocess.run(
            ["git", "merge-base", "--is-ancestor", sha, f"origin/{branch}"],
            cwd=ROOT,
            capture_output=True,
        )
        allowed |= result.returncode == 0
    if not allowed and "GITHUB_ACTIONS" not in os.environ:
        for branch in branches:
            result = subprocess.run(
                ["git", "merge-base", "--is-ancestor", sha, branch],
                cwd=ROOT,
                capture_output=True,
            )
            allowed |= result.returncode == 0
    if not allowed:
        raise ValueError("Release commit must belong to origin/dev, origin/main, or origin/master.")
    data["source_sha"] = sha

    output = os.getenv("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as handle:
            for key in ("tag", "version", "version_code", "channel", "source_sha"):
                handle.write(f"{key}={data[key]}\n")
    print(json.dumps(data))


def check_strings(root: Path = ROOT) -> None:
    es_path = root / "app/src/main/res/values/strings.xml"
    en_path = root / "app/src/main/res/values-en/strings.xml"
    if not es_path.exists() or not en_path.exists():
        raise ValueError("strings.xml resources missing.")

    try:
        es_root = ET.parse(es_path).getroot()
        en_root = ET.parse(en_path).getroot()
    except ET.ParseError as err:
        raise ValueError(f"XML parsing error in strings: {err}")

    es_keys = {el.attrib.get("name") for el in es_root.findall("string") if el.attrib.get("name")}
    en_keys = {el.attrib.get("name") for el in en_root.findall("string") if el.attrib.get("name")}

    missing_in_en = es_keys - en_keys
    if missing_in_en:
        raise ValueError(f"Missing English translations for keys: {sorted(missing_in_en)[:5]}...")


def sha256(path: Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def check_apk(path: Path) -> None:
    if not path.exists():
        raise ValueError(f"APK file does not exist: {path}")
    if not zipfile.is_zipfile(path):
        raise ValueError(f"File is not a valid APK/ZIP: {path.name}")

    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        if "AndroidManifest.xml" not in names:
            raise ValueError(f"APK missing AndroidManifest.xml: {path.name}")
        if not any(name.startswith("classes") and name.endswith(".dex") for name in names):
            raise ValueError(f"APK missing classes.dex: {path.name}")
        if "resources.arsc" not in names:
            raise ValueError(f"APK missing resources.arsc: {path.name}")

    with path.open("rb") as handle:
        apk_bytes = handle.read()
        has_v2_v3 = APK_SIG_BLOCK_MAGIC in apk_bytes
        has_v1 = any(
            re.match(r"^META-INF/.*\.(RSA|DSA|EC)$", name, re.IGNORECASE)
            for name in zipfile.ZipFile(path).namelist()
        )
        if not (has_v2_v3 or has_v1):
            raise ValueError(f"APK is not signed: {path.name}")


def package(tag: str, root: Path = ROOT, output: Path | None = None) -> None:
    data = metadata(tag, root)
    built_apk = root / "app/build/outputs/apk/github/release/app-github-release.apk"
    output_dir = output if output is not None else root / "build/release"
    output_dir.mkdir(parents=True, exist_ok=True)

    check_apk(built_apk)

    target_apk = output_dir / data["apk"]
    shutil.copyfile(built_apk, target_apk)
    shutil.copyfile(root / "release-notes.txt", output_dir / "release-notes.txt")

    data["source_sha"] = run("git", "rev-parse", "HEAD")
    data["repository"] = PUBLIC_REPO
    data["assets"] = {
        data["apk"]: sha256(target_apk)
    }

    release_json = output_dir / "release.json"
    release_json.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

    names = [data["apk"], "release.json"]
    sums = "".join(f"{sha256(output_dir / n)}  {n}\n" for n in names)
    (output_dir / "SHA256SUMS.txt").write_text(sums, encoding="utf-8")
    print(f"Packaged release {tag} in {output_dir}")


def verify_artifacts(output: Path, tag: str, source_sha: str) -> dict:
    expected = parse_tag(tag)
    release_json_path = output / "release.json"
    if not release_json_path.exists():
        raise ValueError("release.json missing from output artifacts.")

    data = json.loads(release_json_path.read_text(encoding="utf-8"))
    if not SHA_PATTERN.fullmatch(source_sha) or any(data.get(k) != v for k, v in expected.items()):
        raise ValueError("Artifact tag/version/channel mismatch.")
    if data.get("source_sha") != source_sha or data.get("repository") != PUBLIC_REPO:
        raise ValueError("Artifact source/destination mismatch.")

    names = {expected["apk"]}
    if set(data.get("assets", {}).keys()) != names:
        raise ValueError("Unexpected public asset names.")

    expected_files = names | {"release.json", "SHA256SUMS.txt", "release-notes.txt"}
    actual_files = {p.name for p in output.iterdir()}
    if actual_files != expected_files:
        raise ValueError(f"Unexpected files in release artifact: {actual_files ^ expected_files}")

    for name, digest in data["assets"].items():
        if sha256(output / name) != digest:
            raise ValueError(f"Artifact checksum mismatch: {name}")

    expected_sums = "".join(f"{sha256(output / n)}  {n}\n" for n in (expected["apk"], "release.json"))
    if (output / "SHA256SUMS.txt").read_text(encoding="utf-8") != expected_sums:
        raise ValueError("Checksum manifest mismatch.")

    return data


def gh_json(*args: str):
    return json.loads(run("gh", *args))


def publication_policy(data: dict, releases: list[dict]) -> tuple[dict | None, bool]:
    existing = next((r for r in releases if r["tag_name"] == data["tag"]), None)
    versions = []
    for release in releases:
        if release.get("draft") or release.get("prerelease"):
            continue
        match = re.fullmatch(r"v?(\d+)\.(\d+)\.(\d+)(?:-stable)?", release["tag_name"])
        if match:
            versions.append(tuple(map(int, match.groups())))
    version = tuple(map(int, data["version"].split(".")))
    latest = data["channel"] == "stable" and (not versions or version >= max(versions))
    return existing, latest


def publish(tag: str, source_sha: str, output: Path | None = None) -> None:
    if not os.getenv("GH_TOKEN"):
        raise ValueError("Configure the RAYOAI_RELEASE_TOKEN Actions secret for the public repository.")
    output = output if output is not None else ROOT / "build/release"
    data = verify_artifacts(output, tag, source_sha)
    repo = gh_json("api", f"repos/{PUBLIC_REPO}")
    if repo.get("private") or repo.get("archived") or repo.get("default_branch") != "main":
        raise ValueError("Public distribution repository configuration changed; review before publishing.")

    pages = gh_json("api", "--paginate", "--slurp", f"repos/{PUBLIC_REPO}/releases?per_page=100")
    existing, latest = publication_policy(data, [release for page in pages for release in page])
    marker = f"RayoAI build: {tag} / {source_sha}"
    names = [data["apk"], "SHA256SUMS.txt", "release.json"]

    if existing:
        if marker not in (existing.get("body") or ""):
            raise ValueError("Existing release is not owned by this build; refusing to modify it.")
        if existing["prerelease"] != (data["channel"] == "beta"):
            raise ValueError("Existing release channel differs from the build.")
        if not existing["draft"]:
            assets = {asset["name"]: asset.get("digest") for asset in existing.get("assets", [])}
            if assets == {n: "sha256:" + sha256(output / n) for n in names}:
                print("Identical release already published; no changes.")
                return
            raise ValueError("Published release differs; use a new version, never overwrite public binaries.")
        if set(a["name"] for a in existing.get("assets", [])) - set(names):
            raise ValueError("Unexpected assets in draft; inspect manually.")

    notes = (output / "release-notes.txt").read_text(encoding="utf-8").strip()
    if not notes:
        raise ValueError("Release notes are empty.")

    if not existing:
        public_sha = gh_json("api", f"repos/{PUBLIC_REPO}/git/ref/heads/main")["object"]["sha"]
        with tempfile.TemporaryDirectory(prefix="rayoai_release_notes_") as temp:
            notes_file = Path(temp) / "notes.txt"
            notes_file.write_text(notes + "\n\n" + marker + "\n", encoding="utf-8")
            run(
                "gh",
                "release",
                "create",
                tag,
                "--repo",
                PUBLIC_REPO,
                "--target",
                public_sha,
                "--title",
                tag,
                "--notes-file",
                str(notes_file),
                "--draft",
                f"--prerelease={'true' if data['channel'] == 'beta' else 'false'}",
                "--latest=false",
            )

    run(
        "gh",
        "release",
        "upload",
        tag,
        "--repo",
        PUBLIC_REPO,
        *(str(output / name) for name in names),
        "--clobber",
    )

    release_id = gh_json("release", "view", tag, "--repo", PUBLIC_REPO, "--json", "databaseId")[
        "databaseId"
    ]
    draft = gh_json("api", f"repos/{PUBLIC_REPO}/releases/{release_id}")
    expected_assets = {n: "sha256:" + sha256(output / n) for n in names}
    actual_assets = {a["name"]: a.get("digest") for a in draft.get("assets", [])}
    if actual_assets != expected_assets:
        raise ValueError(
            f"Uploaded asset hashes differ; release remains a draft. Expected: {expected_assets}, got: {actual_assets}"
        )

    run(
        "gh",
        "release",
        "edit",
        tag,
        "--repo",
        PUBLIC_REPO,
        "--draft=false",
        f"--latest={'true' if latest else 'false'}",
    )
    print(f"Published https://github.com/{PUBLIC_REPO}/releases/tag/{tag}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("validate", "metadata", "strings", "package", "publish"))
    parser.add_argument("--tag")
    parser.add_argument("--source-sha")
    parser.add_argument("--output", type=Path, help="Artifact directory; does not build anything.")
    parser.add_argument(
        "--build-only",
        action="store_true",
        help="Validate a branch build without requiring a source tag.",
    )
    args = parser.parse_args()
    try:
        if args.build_only and args.command != "validate":
            parser.error("--build-only is only valid with validate")
        if args.command in {"validate", "metadata", "package", "publish"} and not args.tag:
            parser.error("--tag is required")
        if args.command == "metadata":
            print(json.dumps(metadata(args.tag)))
        elif args.command == "validate":
            validate(args.tag, build_only=args.build_only)
        elif args.command == "strings":
            check_strings()
            print("Strings validated successfully.")
        elif args.command == "package":
            package(args.tag, output=args.output)
        else:
            publish(args.tag, args.source_sha or "", output=args.output)
    except (ValueError, RuntimeError, OSError, KeyError, EOFError) as exc:
        parser.exit(1, f"Release aborted: {exc}\n")


if __name__ == "__main__":
    main()

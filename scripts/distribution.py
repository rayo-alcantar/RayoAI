"""Build once, verify a paired Android release, publish each destination separately."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request
import xml.etree.ElementTree as ET

from scripts import release

ROOT = release.ROOT
SOURCE_REPO = "rayo-alcantar/RayoAI"
PACKAGE_NAME = "com.rayoai"
BUNDLETOOL_VERSION = "1.18.3"
BUNDLETOOL_SHA256 = "a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
AAB_NAME = "app-play-release.aab"
SET_NAME = "release-set.json"
ARTIFACT_NAME = "android-release-set"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def write_outputs(data: dict) -> None:
    if path := os.getenv("GITHUB_OUTPUT"):
        with open(path, "a", encoding="utf-8") as handle:
            for key, value in data.items():
                value = json.dumps(value, separators=(",", ":")) if isinstance(value, list) else str(value)
                if "\n" in value or "\r" in value:
                    raise ValueError("Invalid multiline workflow output.")
                handle.write(f"{key}={value}\n")
    print(json.dumps(data))


def publication_plan(tag: str, destinations: str, order: str, track: str, status: str,
                     source_ref: str = "", run_id: str = "") -> dict:
    data = release.parse_tag(tag)
    choices = {"none": [], "github": ["github"], "play": ["play"], "both": ["github", "play"]}
    if destinations not in choices or order not in {"parallel", "github-first", "play-first"}:
        raise ValueError("Invalid destinations or publication order.")
    if source_ref and (destinations != "none" or run_id):
        raise ValueError("source_ref is only allowed for a fresh build without publication.")
    if run_id and (not run_id.isascii() or not run_id.isdecimal() or int(run_id) <= 0):
        raise ValueError("artifact_run_id must be a positive Actions run ID.")
    if track == "auto":
        track = "production" if data["channel"] == "stable" else "internal"
    if track not in {"internal", "alpha", "beta", "production"} or status not in {"draft", "completed"}:
        raise ValueError("Invalid Play track or release status.")
    if data["channel"] == "beta" and track == "production":
        raise ValueError("A beta tag cannot be published to Play production.")
    first = choices[destinations]
    second: list[str] = []
    if len(first) == 2 and order != "parallel":
        first = [order.removesuffix("-first")]
        second = ["play" if first == ["github"] else "github"]
    return {"first": first, "second": second, "play_track": track, "play_status": status}


def validate_inspection_request(*, destination: str, tag: str, source_sha: str, caller_sha: str,
                                run_id: str, track: str, status: str, send_for_review: str) -> None:
    """Keep credential checks separate from every publication/artifact path."""
    if (destination != "play" or tag or run_id or track != "internal" or status != "draft"
            or send_for_review != "false" or not release.SHA_PATTERN.fullmatch(source_sha)
            or source_sha != caller_sha):
        raise ValueError("Access inspection requires the caller commit, Play internal/draft, no review, tag or artifacts.")


def verify_run(run_id: str, source_sha: str) -> None:
    """Accept only this repository's verified build, never an arbitrary uploaded ZIP."""
    if not run_id.isascii() or not run_id.isdecimal() or int(run_id) <= 0:
        raise ValueError("Invalid artifact run ID.")
    repo = os.getenv("GITHUB_REPOSITORY", SOURCE_REPO)
    if repo.lower() != SOURCE_REPO.lower():
        raise ValueError("Only the RayoAI source repository can supply distribution artifacts.")
    run = release.gh_json("api", f"repos/{repo}/actions/runs/{run_id}")
    if (run.get("head_sha") != source_sha or run.get("path") != ".github/workflows/release.yml"
            or run.get("event") not in {"push", "workflow_dispatch"}):
        raise ValueError("Artifact run does not belong to this tagged release workflow/commit.")
    # A publication failure must not force a rebuild of an already successful build.
    pages = release.gh_json("api", "--paginate", "--slurp",
                            f"repos/{repo}/actions/runs/{run_id}/jobs?per_page=100&filter=all")
    jobs = [job for page in pages for job in page["jobs"]]
    if not any(job.get("name") == "Build paired artifacts" and job.get("conclusion") == "success" for job in jobs):
        raise ValueError("The source run has no successful paired build job.")
    pages = release.gh_json("api", "--paginate", "--slurp",
                            f"repos/{repo}/actions/runs/{run_id}/artifacts?per_page=100")
    artifacts = [a for page in pages for a in page["artifacts"] if a["name"] == ARTIFACT_NAME]
    if len(artifacts) != 1 or artifacts[0].get("expired"):
        raise ValueError("Paired artifact is missing, ambiguous or expired; do not silently rebuild this version.")


def artifact_origin(source_sha: str, requested: str = "") -> dict:
    if not release.SHA_PATTERN.fullmatch(source_sha):
        raise ValueError("Invalid source SHA.")
    if requested:
        verify_run(requested, source_sha)
        return {"run_id": requested, "reuse": "true"}
    repo = os.getenv("GITHUB_REPOSITORY", SOURCE_REPO)
    if repo.lower() != SOURCE_REPO.lower():
        raise ValueError("Unexpected source repository.")
    pages = release.gh_json("api", "--paginate", "--slurp",
                           f"repos/{repo}/actions/artifacts?name={ARTIFACT_NAME}&per_page=100")
    artifacts = [a for page in pages for a in page["artifacts"]
                 if a["name"] == ARTIFACT_NAME and a.get("workflow_run", {}).get("head_sha") == source_sha]
    if artifacts:
        ids = {str(a["workflow_run"]["id"]) for a in artifacts}
        if len(ids) != 1:
            raise ValueError("Multiple paired builds exist for this SHA; select the original artifact_run_id explicitly.")
        run_id = ids.pop()
        verify_run(run_id, source_sha)
        return {"run_id": run_id, "reuse": "true"}
    # Retention may have removed the artifact. A past successful build is still
    # evidence that this version must not be silently reconstructed.
    pages = release.gh_json("api", "--paginate", "--slurp",
                           f"repos/{repo}/actions/workflows/release.yml/runs?head_sha={source_sha}&per_page=100")
    for page in pages:
        for run in page["workflow_runs"]:
            jobs = release.gh_json("api", "--paginate", "--slurp",
                                  f"repos/{repo}/actions/runs/{run['id']}/jobs?filter=all&per_page=100")
            if any(job.get("name") == "Build paired artifacts" and job.get("conclusion") == "success"
                   for job_page in jobs for job in job_page["jobs"]):
                raise ValueError("A paired build already succeeded but its artifact is missing. Recover the original pair; do not rebuild this version.")
    return {"run_id": os.environ["GITHUB_RUN_ID"], "reuse": "false"}


def bundletool(path: Path) -> None:
    """Download only a pinned upstream binary and verify before Java can execute it."""
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        url = f"https://github.com/google/bundletool/releases/download/{BUNDLETOOL_VERSION}/bundletool-all-{BUNDLETOOL_VERSION}.jar"
        with urllib.request.urlopen(url, timeout=120) as response, path.open("xb") as handle:
            shutil.copyfileobj(response, handle)
    if release.sha256(path) != BUNDLETOOL_SHA256:
        raise ValueError("bundletool checksum differs from the pinned upstream release.")


def tool_output(*args: str, allowed: tuple[int, ...] = (0,)) -> str:
    result = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode not in allowed:
        raise ValueError(f"Binary verification failed ({Path(args[0]).name}, exit {result.returncode}).")
    return result.stdout + result.stderr


def inspect_binaries(apk: Path, aab: Path, tools: Path, jar: Path) -> dict:
    bundletool(jar)
    ext = ".exe" if os.name == "nt" else ""
    badging = tool_output(str(tools / f"aapt2{ext}"), "dump", "badging", str(apk))
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not match:
        raise ValueError("Cannot read APK identity.")
    apk_identity = {"package_name": match[1], "version_code": int(match[2]), "version": match[3]}
    manifest = tool_output("java", "-jar", str(jar), "dump", "manifest", f"--bundle={aab}")
    xml = ET.fromstring(manifest)
    aab_identity = {"package_name": xml.attrib["package"],
                    "version_code": int(xml.attrib[ANDROID_NS + "versionCode"]),
                    "version": xml.attrib[ANDROID_NS + "versionName"]}
    if apk_identity != aab_identity:
        raise ValueError("APK and AAB package/versionName/versionCode differ.")
    signer = tools / ("apksigner.bat" if os.name == "nt" else "apksigner")
    cert = tool_output(str(signer), "verify", "--print-certs", str(apk))
    apk_cert = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", cert)
    verified = tool_output("jarsigner", "-J-Duser.language=en", "-verify", "-strict", str(aab), allowed=(0, 4))
    if "jar verified" not in verified or "unsigned entries" in verified:
        raise ValueError("AAB signature verification failed or contains unsigned entries.")
    cert = tool_output("keytool", "-J-Duser.language=en", "-printcert", "-jarfile", str(aab))
    aab_cert = re.findall(r"SHA256: ([0-9A-Fa-f:]+)", cert)
    if len(apk_cert) != 1 or len(aab_cert) != 1 or apk_cert[0].lower() != aab_cert[0].replace(":", "").lower():
        raise ValueError("APK signer and AAB upload-key signer differ.")
    tool_output("java", "-jar", str(jar), "validate", f"--bundle={aab}")
    return apk_identity | {"upload_certificate_sha256": apk_cert[0].lower()}


def play_notes(root: Path) -> list[dict]:
    notes = []
    for path in sorted((root / "play-release-notes").glob("*.txt")):
        if not re.fullmatch(r"[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*", path.stem):
            raise ValueError("Play notes filename must be a BCP-47 locale.")
        text = path.read_text(encoding="utf-8").strip()
        if not text or len(text) > 500:
            raise ValueError(f"Play release notes must contain 1-500 characters: {path.name}")
        notes.append({"language": path.stem, "text": text})
    if not notes:
        raise ValueError("Add at least one play-release-notes/<locale>.txt (maximum 500 characters).")
    return notes


def package_set(tag: str, tools: Path, jar: Path, output: Path, root: Path = ROOT) -> dict:
    if release.run("git", "status", "--porcelain", "--untracked-files=normal"):
        raise ValueError("Publishable pairs require clean committed source; inspect local binaries separately for validation.")
    data = release.metadata(tag, root)
    apk = root / "app/build/outputs/apk/github/release" / data["apk"]
    aab = root / "app/build/outputs/bundle/playRelease" / AAB_NAME
    identity = inspect_binaries(apk, aab, tools, jar)
    if (identity["package_name"] != PACKAGE_NAME or identity["version"] != data["version"]
            or identity["version_code"] != data["version_code"]):
        raise ValueError("Built binary identity does not match release source metadata.")
    notes = play_notes(root)
    if output.exists() and any(output.iterdir()):
        raise ValueError("Output directory is not empty; preserve it and select a new --output directory.")
    release.package(tag, root, output / "github")
    play_dir = output / "play"
    play_dir.mkdir(parents=True)
    shutil.copyfile(aab, play_dir / AAB_NAME)
    (play_dir / "release-notes.json").write_text(json.dumps(notes, ensure_ascii=False) + "\n", encoding="utf-8")
    data.update(identity)
    data.update(schema=1, source_sha=release.run("git", "rev-parse", "HEAD"),
                public_repository=release.PUBLIC_REPO,
                files={p.relative_to(output).as_posix(): release.sha256(p)
                       for p in sorted(output.rglob("*")) if p.is_file()})
    (output / SET_NAME).write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    verify_set(output, tag, data["source_sha"])
    return data


def verify_set(output: Path, tag: str, source_sha: str) -> dict:
    data = json.loads((output / SET_NAME).read_text(encoding="utf-8"))
    expected = release.parse_tag(tag)
    if (data.get("schema") != 1 or any(data.get(k) != v for k, v in expected.items())
            or data.get("source_sha") != source_sha or not release.SHA_PATTERN.fullmatch(source_sha)
            or data.get("package_name") != PACKAGE_NAME or data.get("public_repository") != release.PUBLIC_REPO
            or type(data.get("version_code")) is not int or not 0 < data["version_code"] < 2100000000):
        raise ValueError("Paired artifact identity/tag/source mismatch.")
    names = {"github/app-github-release.apk", "github/release.json", "github/SHA256SUMS.txt",
             "github/release-notes.txt", f"play/{AAB_NAME}", "play/release-notes.json"}
    actual = {p.relative_to(output).as_posix() for p in output.rglob("*") if p.is_file()}
    if set(data.get("files", {})) != names or actual != names | {SET_NAME}:
        raise ValueError("Unexpected files in paired release artifact.")
    for name in names:
        path = output / name
        if path.is_symlink() or release.sha256(path) != data["files"][name]:
            raise ValueError(f"Paired artifact checksum mismatch: {name}")
    public = release.verify_artifacts(output / "github", tag, source_sha)
    if public.get("version_code") != data["version_code"]:
        raise ValueError("Public and Play versionCode differ.")
    notes = json.loads((output / "play/release-notes.json").read_text(encoding="utf-8"))
    if not notes or any(set(n) != {"language", "text"} or not 0 < len(n["text"]) <= 500 for n in notes):
        raise ValueError("Invalid packaged Play release notes.")
    return data


def dispatch(tag: str, run_id: str, destinations: str, order: str, track: str,
             status: str, send_for_review: bool) -> None:
    """Queue publication only on explicit CLI invocation, always reusing a proven pair."""
    publication_plan(tag, destinations, order, track, status, run_id=run_id)
    if destinations == "none" or not run_id:
        raise ValueError("Publication requires --destinations github/play/both and the original --run-id.")
    obj = release.gh_json("api", f"repos/{SOURCE_REPO}/git/ref/tags/{tag}")["object"]
    while obj["type"] == "tag":
        obj = release.gh_json("api", f"repos/{SOURCE_REPO}/git/tags/{obj['sha']}")["object"]
    if obj["type"] != "commit":
        raise ValueError("Source tag does not identify a commit.")
    verify_run(run_id, obj["sha"])
    release.run("gh", "workflow", "run", "release.yml", "--repo", SOURCE_REPO, "--ref", "dev",
                "-f", f"tag={tag}", "-f", f"artifact_run_id={run_id}", "-f", f"destinations={destinations}",
                "-f", f"publication_order={order}", "-f", f"play_track={track}", "-f", f"play_status={status}",
                "-f", f"send_for_review={str(send_for_review).lower()}")
    print("Workflow queued, not yet deployed. Inspect Actions and each store's resulting state.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("plan", "package", "verify", "verify-run", "origin", "tools", "dispatch", "check-access-mode"))
    parser.add_argument("--tag", default=os.getenv("RELEASE_TAG", ""))
    parser.add_argument("--source-sha", default=os.getenv("SOURCE_SHA", ""))
    parser.add_argument("--run-id", default=os.getenv("ARTIFACT_RUN_ID", ""))
    parser.add_argument("--output", type=Path, default=ROOT / "build/distribution")
    parser.add_argument("--tools", type=Path, default=Path(os.getenv("SDK_BUILD_TOOLS", ".")))
    parser.add_argument("--bundletool", type=Path, default=ROOT / "build/tools/bundletool.jar")
    parser.add_argument("--destinations", choices=("none", "github", "play", "both"), default="none")
    parser.add_argument("--order", choices=("parallel", "github-first", "play-first"), default="parallel")
    parser.add_argument("--track", default="auto")
    parser.add_argument("--status", choices=("draft", "completed"), default="draft")
    parser.add_argument("--send-for-review", action="store_true")
    args = parser.parse_args()
    try:
        if args.command == "plan":
            write_outputs(publication_plan(args.tag, os.getenv("RELEASE_DESTINATIONS", "none"),
                          os.getenv("RELEASE_ORDER", "parallel"), os.getenv("PLAY_TRACK", "auto"),
                          os.getenv("PLAY_STATUS", "completed"), os.getenv("SOURCE_REF", ""), args.run_id))
        elif args.command == "check-access-mode":
            validate_inspection_request(destination=os.getenv("DESTINATION", ""), tag=args.tag,
                                        source_sha=args.source_sha, caller_sha=os.getenv("GITHUB_SHA", ""),
                                        run_id=args.run_id, track=os.getenv("PLAY_TRACK", ""),
                                        status=os.getenv("PLAY_STATUS", ""),
                                        send_for_review=os.getenv("SEND_FOR_REVIEW", ""))
        elif args.command == "tools":
            bundletool(args.bundletool)
        elif args.command == "verify-run":
            verify_run(args.run_id, args.source_sha)
        elif args.command == "origin":
            write_outputs(artifact_origin(args.source_sha, args.run_id))
        elif args.command == "verify":
            print(json.dumps(verify_set(args.output, args.tag, args.source_sha)))
        elif args.command == "dispatch":
            dispatch(args.tag, args.run_id, args.destinations, args.order, args.track, args.status, args.send_for_review)
        else:
            print(json.dumps(package_set(args.tag, args.tools, args.bundletool, args.output)))
    except (ValueError, RuntimeError, OSError, KeyError, ET.ParseError) as exc:
        parser.exit(1, f"Distribution aborted: {exc}\n")


if __name__ == "__main__":
    main()

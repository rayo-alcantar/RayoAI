"""Narrow Android Publisher client. No billing, users, listings, or device access.

Authentication is an ephemeral OAuth token from google-github-actions/auth.
All writes are explicit; a network failure is never automatically replayed.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener

from scripts import distribution, release

API_ROOT = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
UPLOAD_ROOT = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/"


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward a publisher bearer token to another host.
        return None


class PlayAPI:
    def __init__(self, token: str):
        if not token or any(char in token for char in "\r\n"):
            raise ValueError("Set GOOGLE_PLAY_ACCESS_TOKEN; never pass credentials on the command line.")
        self.token = token
        self.opener = build_opener(NoRedirect())

    def request(self, method: str, path: str, *, body=None, file: Path | None = None,
                params: dict | None = None) -> dict:
        url = (UPLOAD_ROOT if file else API_ROOT) + distribution.PACKAGE_NAME + "/" + path
        if params:
            url += "?" + urlencode(params)
        headers = {"Authorization": f"Bearer {self.token}", "Accept": "application/json"}
        data = json.dumps(body).encode("utf-8") if body is not None else None
        if data is not None:
            headers["Content-Type"] = "application/json"
        handle = None
        try:
            if file:
                handle = file.open("rb")
                data = handle
                headers.update({"Content-Type": "application/octet-stream", "Content-Length": str(file.stat().st_size)})
            request = Request(url, data=data, headers=headers, method=method)
            with self.opener.open(request, timeout=300) as response:
                raw = response.read(16 * 1024 * 1024 + 1)
                if len(raw) > 16 * 1024 * 1024:
                    raise ValueError("Unexpectedly large Play API response.")
                return json.loads(raw) if raw else {}
        except HTTPError as exc:
            try:
                message = json.loads(exc.read(64 * 1024)).get("error", {}).get("message", "Request rejected")
            except (ValueError, AttributeError):
                message = "Request rejected"
            finally:
                exc.close()
            message = str(message).replace(self.token, "[REDACTED]")[:1500]
            raise RuntimeError(f"Play API HTTP {exc.code}: {message}") from None
        except (URLError, TimeoutError, OSError) as exc:
            raise RuntimeError("Play API network failure. The last operation may have succeeded; "
                               "inspect Play Console before retrying. No automatic retry was performed.") from None
        finally:
            if handle:
                handle.close()


def edit_path(edit_id: str, suffix: str = "") -> str:
    return "edits/" + quote(edit_id, safe="") + suffix


def bundle_matches(bundle: dict, data: dict) -> bool:
    return (str(bundle.get("versionCode")) == str(data["version_code"])
            and bundle.get("sha256", "").lower() == data["files"][f"play/{distribution.AAB_NAME}"])


def track_update(current: dict, data: dict, track: str, status: str, notes: list[dict]) -> dict | None:
    if track not in {"internal", "alpha", "beta", "production"} or status not in {"draft", "completed"}:
        raise ValueError("Unsupported Play track/status.")
    if data["channel"] == "beta" and track == "production":
        raise ValueError("Beta tags cannot go to production.")
    code = str(data["version_code"])
    releases = current.get("releases", [])
    same = [r for r in releases if code in r.get("versionCodes", [])]
    if same and len(same) == 1 and same[0].get("status") == status and same[0].get("versionCodes") == [code]:
        return None  # Idempotent, including retry after a lost commit response.
    for item in releases:
        if any(int(v) > int(code) for v in item.get("versionCodes", [])):
            raise ValueError("Refusing to roll a Play track back to an older versionCode.")
        if item.get("status") in {"inProgress", "halted"}:
            raise ValueError("An existing rollout needs manual resolution; refusing to replace it.")
        if item.get("status") == "draft" and item.get("versionCodes") != [code]:
            raise ValueError("Another Play draft exists; refusing to replace someone else's work.")
        if code in item.get("versionCodes", []) and item.get("status") == "completed" and status == "draft":
            raise ValueError("Refusing to demote a completed release to draft.")
    desired = {"name": data["tag"], "versionCodes": [code], "status": status, "releaseNotes": notes}
    # Adding a draft must not remove the currently distributed release.
    retained = [r for r in releases if r.get("status") == "completed"] if status == "draft" else []
    return {"track": track, "releases": retained + [desired]}


def inspect(api: PlayAPI) -> dict:
    """A temporary edit is opened and deleted. No upload/track update/commit."""
    edit = api.request("POST", "edits", body={})["id"]
    try:
        tracks = api.request("GET", edit_path(edit, "/tracks")).get("tracks", [])
        bundles = api.request("GET", edit_path(edit, "/bundles")).get("bundles", [])
        return {"package_name": distribution.PACKAGE_NAME,
                "highest_bundle_version_code": max((int(b["versionCode"]) for b in bundles), default=0),
                "tracks": [{"track": t["track"], "releases": [
                    {"versionCodes": r.get("versionCodes", []), "status": r.get("status")}
                    for r in t.get("releases", [])]} for t in tracks]}
    finally:
        api.request("DELETE", edit_path(edit))


def verify_remote(api: PlayAPI, data: dict, track: str, status: str) -> None:
    edit = api.request("POST", "edits", body={})["id"]
    try:
        bundles = api.request("GET", edit_path(edit, "/bundles")).get("bundles", [])
        target = api.request("GET", edit_path(edit, "/tracks/" + quote(track, safe="")))
        if not any(bundle_matches(b, data) for b in bundles) or not any(
            r.get("versionCodes") == [str(data["version_code"])] and r.get("status") == status
            for r in target.get("releases", [])
        ):
            raise RuntimeError("Play commit returned but remote bundle/track verification failed; inspect Console.")
    finally:
        api.request("DELETE", edit_path(edit))


def publish(api: PlayAPI, output: Path, tag: str, sha: str, track: str, status: str,
            send_for_review: bool) -> dict:
    data = distribution.verify_set(output, tag, sha)
    notes = json.loads((output / "play/release-notes.json").read_text(encoding="utf-8"))
    edit = api.request("POST", "edits", body={})["id"]
    committed = False
    try:
        tracks = api.request("GET", edit_path(edit, "/tracks")).get("tracks", [])
        current = next((t for t in tracks if t["track"] == track), {"track": track})
        update = track_update(current, data, track, status, notes)
        bundles = api.request("GET", edit_path(edit, "/bundles")).get("bundles", [])
        same = next((b for b in bundles if str(b["versionCode"]) == str(data["version_code"])), None)
        if same and not bundle_matches(same, data):
            raise ValueError("This Play versionCode already belongs to different AAB bytes. Use a new version; never rebuild it.")
        if update is None:
            if not same:
                raise ValueError("Track references this code but its matching bundle cannot be verified.")
            return {"result": "already_in_track", "track": track, "status": status, "version_code": data["version_code"]}
        if same is None and any(int(b["versionCode"]) > data["version_code"] for b in bundles):
            raise ValueError("A newer Play bundle already exists. Inspect Console instead of publishing an older build.")
        if same is None:
            uploaded = api.request("POST", edit_path(edit, "/bundles"),
                                   file=output / "play" / distribution.AAB_NAME, params={"uploadType": "media"})
            if not bundle_matches(uploaded, data):
                raise ValueError("Google returned a different AAB hash/versionCode; track was not updated.")
        api.request("PUT", edit_path(edit, "/tracks/" + quote(track, safe="")), body=update)
        api.request("POST", edit_path(edit, ":validate"))
        # Never implicitly cancel an existing review or silently switch review intent.
        api.request("POST", edit_path(edit, ":commit"), params={
            "changesNotSentForReview": str(not send_for_review).lower(),
            "changesInReviewBehavior": "ERROR_IF_IN_REVIEW",
        })
        committed = True
    finally:
        if not committed:
            # Cleanup is best-effort: never hide the original failure/ambiguous commit.
            try:
                api.request("DELETE", edit_path(edit))
            except (RuntimeError, ValueError):
                print("Temporary Play edit could not be discarded; inspect Console.", file=sys.stderr)
    verify_remote(api, data, track, status)
    return {"result": "accepted_in_track", "track": track, "status": status,
            "version_code": data["version_code"], "review_requested": send_for_review,
            "availability": "Check Play Console: review and managed publishing may delay user availability."}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("inspect", "publish"))
    parser.add_argument("--tag", default=os.getenv("RELEASE_TAG", ""))
    parser.add_argument("--source-sha", default=os.getenv("SOURCE_SHA", ""))
    parser.add_argument("--output", type=Path, default=release.ROOT / "build/distribution")
    parser.add_argument("--track", default=os.getenv("PLAY_TRACK", "internal"))
    parser.add_argument("--status", default=os.getenv("PLAY_STATUS", "draft"))
    parser.add_argument("--send-for-review", choices=("true", "false"), default=os.getenv("SEND_FOR_REVIEW", "false"))
    args = parser.parse_args()
    try:
        api = PlayAPI(os.getenv("GOOGLE_PLAY_ACCESS_TOKEN", ""))
        result = inspect(api) if args.command == "inspect" else publish(
            api, args.output, args.tag, args.source_sha, args.track, args.status, args.send_for_review == "true")
        print(json.dumps(result))
        if path := os.getenv("GITHUB_STEP_SUMMARY"):
            with open(path, "a", encoding="utf-8") as handle:
                handle.write("\n### Google Play\n```json\n" + json.dumps(result, indent=2) + "\n```\n")
    except (ValueError, RuntimeError, OSError, KeyError) as exc:
        parser.exit(1, f"Play operation stopped: {exc}\n")


if __name__ == "__main__":
    main()

"""Post release notes and download links to a QC Salon forum topic.

This tool runs as part of the release workflow after a GitHub release is created.
Credentials must be provided via QCSALON_USERNAME and QCSALON_PASSWORD environment variables.
"""
from __future__ import annotations

import argparse
from html.parser import HTMLParser
import http.cookiejar
import os
from pathlib import Path
import sys
import urllib.parse
import urllib.request

from scripts import release

DEFAULT_TOPIC_URL = "https://qcsalon.net/es/forum5000000/topic147452"
LOGIN_URL = "https://qcsalon.net/es/login"
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) RayoAI-Release-Notifier"


class FormParser(HTMLParser):
    """Extract forms, inputs, and textareas from an HTML document."""

    def __init__(self) -> None:
        super().__init__()
        self.forms: list[dict] = []
        self._current_form: dict | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attrs_dict = {k: v or "" for k, v in attrs}
        if tag == "form":
            self._current_form = {
                "action": attrs_dict.get("action", ""),
                "method": attrs_dict.get("method", "GET").upper(),
                "id": attrs_dict.get("id", ""),
                "inputs": {},
                "textarea": None,
            }
        elif self._current_form is not None:
            if tag == "input":
                name = attrs_dict.get("name")
                if name:
                    self._current_form["inputs"][name] = attrs_dict.get("value", "")
            elif tag == "textarea":
                self._current_form["textarea"] = attrs_dict.get("name", "message")

    def handle_endtag(self, tag: str) -> None:
        if tag == "form" and self._current_form is not None:
            self.forms.append(self._current_form)
            self._current_form = None


def format_message(tag: str, release_notes: str) -> str:
    """Format the community forum release announcement."""
    parsed = release.parse_tag(tag)
    version = parsed["version"]
    channel = parsed["channel"]
    apk_name = parsed["apk"]
    repo = release.PUBLIC_REPO
    download_url = f"https://github/{repo}/releases/download/{tag}/{apk_name}".replace("https://github/", "https://github.com/")
    release_url = f"https://github/{repo}/releases/tag/{tag}".replace("https://github/", "https://github.com/")

    lines = [
        f"¡Nueva versión disponible! RayoAI {version} ({channel})",
        "",
        "Notas de la versión:",
        release_notes.strip(),
        "",
        f"Descarga directa del APK: {download_url}",
        f"Ver release en GitHub: {release_url}",
    ]
    return "\n".join(lines)


def post_to_topic(
    topic_url: str,
    message: str,
    username: str,
    password: str,
    *,
    opener: urllib.request.OpenerDirector | None = None,
) -> None:
    """Authenticate and submit the message to the target QC Salon forum topic."""
    if not username or not password:
        raise ValueError("QC Salon username and password must not be empty.")

    if opener is None:
        cj = http.cookiejar.CookieJar()
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))

    parsed_topic = urllib.parse.urlparse(topic_url)
    referer_path = parsed_topic.path

    # Step 1: Login
    login_data = urllib.parse.urlencode({
        "login": username,
        "password": password,
        "autoconnect": "on",
        "referer": referer_path,
    }).encode("utf-8")

    login_req = urllib.request.Request(
        LOGIN_URL,
        data=login_data,
        headers={
            "User-Agent": USER_AGENT,
            "Referer": LOGIN_URL,
            "Content-Type": "application/x-www-form-urlencoded",
        },
        method="POST",
    )
    opener.open(login_req)

    # Step 2: Fetch target topic page
    topic_req = urllib.request.Request(
        topic_url,
        headers={
            "User-Agent": USER_AGENT,
            "Referer": LOGIN_URL,
        },
    )
    with opener.open(topic_req) as resp:
        html = resp.read().decode("utf-8", errors="ignore")

    # Step 3: Validate session
    if 'id="loginform"' in html or "Usted debe estar conectado" in html:
        raise RuntimeError("QC Salon login failed. Please check QCSALON_USERNAME and QCSALON_PASSWORD.")

    # Step 4: Extract reply form
    parser = FormParser()
    parser.feed(html)
    reply_form = next((f for f in parser.forms if f.get("textarea")), None)
    if not reply_form:
        raise RuntimeError(f"Could not find reply form on {topic_url}. Thread may be locked or layout changed.")

    action_url = urllib.parse.urljoin(topic_url, reply_form["action"])
    payload = dict(reply_form["inputs"])
    textarea_name = reply_form["textarea"]
    payload[textarea_name] = message

    # Step 5: Post reply
    post_data = urllib.parse.urlencode(payload).encode("utf-8")
    post_req = urllib.request.Request(
        action_url,
        data=post_data,
        headers={
            "User-Agent": USER_AGENT,
            "Referer": topic_url,
            "Content-Type": "application/x-www-form-urlencoded",
        },
        method="POST",
    )
    with opener.open(post_req) as post_resp:
        if getattr(post_resp, "status", 200) >= 400:
            raise RuntimeError(f"QC Salon post failed with status code {post_resp.status}")

    print(f"Successfully posted release notification to {topic_url}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True, help="Release tag (e.g. 3.3.12-stable)")
    parser.add_argument("--notes-file", type=Path, help="Path to release-notes.txt")
    parser.add_argument("--topic-url", default=DEFAULT_TOPIC_URL, help="QC Salon topic URL")
    args = parser.parse_args()

    username = os.getenv("QCSALON_USERNAME", "").strip()
    password = os.getenv("QCSALON_PASSWORD", "").strip()
    if not username or not password:
        print("QCSALON_USERNAME or QCSALON_PASSWORD not set; skipping QC Salon notification.")
        return

    if args.notes_file and args.notes_file.exists():
        notes_path = args.notes_file
    else:
        notes_path = Path(__file__).resolve().parents[1] / "release-notes.txt"

    if not notes_path.exists():
        raise FileNotFoundError(f"Release notes file not found: {notes_path}")
    notes = notes_path.read_text(encoding="utf-8").strip()

    msg = format_message(args.tag, notes)
    try:
        post_to_topic(args.topic_url, msg, username, password)
    except Exception as exc:
        print(f"Error publishing to QC Salon: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()

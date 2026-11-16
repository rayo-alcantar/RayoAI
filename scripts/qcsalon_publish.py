"""Announce a published Android release in the RayoAI topic on QC Salon."""

from __future__ import annotations

import argparse
from html.parser import HTMLParser
import http.client
import http.cookiejar
import json
import os
from pathlib import Path
import re
import ssl
import urllib.error
import urllib.parse
import urllib.request

from scripts import release

BASE_URL = "https://qcsalon.net"
TOPIC_URL = f"{BASE_URL}/es/forum5000000/topic147452"
LOGIN_URL = f"{BASE_URL}/es/login"
TOPIC_PAGE = re.compile(r"/es/forum\d+/topic147452(?:p([1-9]\d*))?")
USER_AGENT = "RayoAI-Android-Release-Notifier"
TIMEOUT = 20
MAX_PAGES = 40
QCSALON_CHAIN = Path(__file__).with_name("qcsalon-chain.pem")


def release_url(tag: str) -> str:
    return f"https://github.com/{release.PUBLIC_REPO}/releases/tag/{tag}"


def published_notes(tag: str) -> str:
    """Read the published release notes without a GitHub write token."""
    data = release.parse_tag(tag)
    api_url = f"https://api.github.com/repos/{release.PUBLIC_REPO}/releases/tags/{tag}"
    req = urllib.request.Request(api_url, headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": USER_AGENT,
    })
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as response:
            publication = json.load(response)
    except (OSError, ValueError, http.client.HTTPException) as exc:
        raise _request_failure("GitHub", "read the public release", exc) from None
    if (
        not isinstance(publication, dict)
        or publication.get("draft") is not False
        or publication.get("prerelease") is not (data["channel"] == "beta")
        or publication.get("tag_name") != tag
        or not isinstance(publication.get("assets"), list)
        or not isinstance(publication.get("body"), str)
    ):
        raise RuntimeError(f"GitHub has no matching public Android release for {tag}.")
    assets = {asset["name"] for asset in publication["assets"]
              if isinstance(asset, dict) and asset.get("state") == "uploaded"
              and isinstance(asset.get("name"), str)}
    if data["apk"] not in assets:
        raise RuntimeError("GitHub release is missing its Android APK.")
    notes, _, marker = publication["body"].replace("\r\n", "\n").strip().rpartition("\n\n")
    prefix = f"RayoAI build: {tag} / "
    if not notes.strip() or not marker.startswith(prefix) or not release.SHA_PATTERN.fullmatch(marker[len(prefix):]):
        raise RuntimeError("GitHub release does not have the expected Android build marker and notes.")
    return notes


def format_message(tag: str, notes: str) -> str:
    data = release.parse_tag(tag)
    if not notes.strip():
        raise ValueError("Release notes are empty.")
    channel = "estable" if data["channel"] == "stable" else "beta"
    assets = f"https://github.com/{release.PUBLIC_REPO}/releases/download/{tag}"
    return "\n".join((
        f"Nueva versión de RayoAI para Android: {data['version']} ({channel})",
        "",
        "Notas de la versión:",
        notes.strip(),
        "",
        f"Descarga directa del APK: {assets}/{data['apk']}",
        f"Ver la versión en GitHub: {release_url(tag)}",
    ))


def _same_site(url: str) -> bool:
    parsed = urllib.parse.urlsplit(url)
    return parsed.scheme == "https" and parsed.netloc == "qcsalon.net"


class _SameSiteRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not _same_site(newurl):
            raise _UnsafeRedirect
        return super().redirect_request(req, fp, code, msg, headers, newurl)


class _UnsafeRedirect(RuntimeError):
    pass


def _request_failure(service: str, stage: str, error: Exception) -> RuntimeError:
    # Never stringify request/response exceptions: they can contain URLs,
    # headers, form data or server echoes of credentials.
    if isinstance(error, urllib.error.HTTPError):
        detail = f"HTTP {error.code}"
    elif isinstance(error, _UnsafeRedirect):
        detail = "redirect outside the expected HTTPS origin"
    elif isinstance(error, urllib.error.URLError) and isinstance(error.reason, ssl.SSLCertVerificationError):
        detail = f"TLS certificate verification, code {error.reason.verify_code}"
    elif isinstance(error, urllib.error.URLError) and isinstance(error.reason, BaseException):
        detail = type(error.reason).__name__
    else:
        detail = type(error).__name__
    return RuntimeError(f"{service}: {stage} failed ({detail}).")


def _forum_ssl_context() -> ssl.SSLContext:
    """Complete QC Salon's missing chain without trusting new roots."""
    context = ssl.create_default_context()
    # Python 3.13 otherwise allows a loaded intermediate to be a trust anchor.
    context.verify_flags &= ~ssl.VERIFY_X509_PARTIAL_CHAIN
    context.load_verify_locations(cafile=QCSALON_CHAIN)
    return context


class TopicPage(HTMLParser):
    def __init__(self, url: str, html: str) -> None:
        super().__init__(convert_charrefs=True)
        self.url = url
        self.links: set[str] = set()
        self.forms: list[dict] = []
        self._form: dict | None = None
        self.feed(html)

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        a = {key: value or "" for key, value in attrs}
        if tag == "a" and a.get("href"):
            self.links.add(urllib.parse.urljoin(self.url, a["href"]))
        elif tag == "form":
            self._form = {
                "id": a.get("id"),
                "action": urllib.parse.urljoin(self.url, a.get("action") or self.url),
                "method": a.get("method", "get").lower(),
                "fields": {},
                "textarea": "",
            }
        elif self._form is not None and a.get("name"):
            if tag == "textarea":
                self._form["textarea"] = a["name"]
            elif tag == "input":
                kind = a.get("type", "text").lower()
                if "disabled" not in a and (kind not in {"checkbox", "radio"} or "checked" in a):
                    self._form["fields"][a["name"]] = a.get("value", "")

    def handle_endtag(self, tag: str) -> None:
        if tag == "form" and self._form is not None:
            self.forms.append(self._form)
            self._form = None

    def topic_links(self) -> dict[int, str]:
        pages = {}
        for url in sorted(self.links):
            if _same_site(url) and (match := TOPIC_PAGE.fullmatch(urllib.parse.urlsplit(url).path)):
                pages[int(match[1] or "1")] = urllib.parse.urljoin(BASE_URL, match[0])
        return pages


def _request(opener, url: str, *, stage: str, referer: str, fields: dict | None = None) -> TopicPage:
    if not _same_site(url):
        raise RuntimeError(f"QC Salon: {stage} blocked (form URL outside the expected HTTPS origin).")
    data = urllib.parse.urlencode(fields).encode("utf-8") if fields is not None else None
    req = urllib.request.Request(url, data=data, headers={"User-Agent": USER_AGENT, "Referer": referer})
    try:
        with opener.open(req, timeout=TIMEOUT) as response:
            return TopicPage(response.geturl(), response.read().decode("utf-8", errors="replace"))
    except (_UnsafeRedirect, OSError, ValueError, http.client.HTTPException) as exc:
        raise _request_failure("QC Salon", stage, exc) from None


def _find_announcement(opener, marker: str, *, stage: str) -> tuple[bool, TopicPage]:
    page = _request(opener, TOPIC_URL, stage=stage, referer=TOPIC_URL)
    if any(form["id"] == "loginform" for form in page.forms):
        raise RuntimeError("QC Salon sign-in did not succeed.")
    latest = page
    latest_number = 1
    pending = page.topic_links()
    visited = {1}
    while True:
        if marker in page.links:
            return True, page
        remaining = pending.keys() - visited
        if not remaining:
            return False, latest
        if len(visited) >= MAX_PAGES:
            raise RuntimeError("Could not check the full QC Salon topic for an existing announcement.")
        number = max(remaining)
        page = _request(opener, pending[number], stage=f"{stage} (page {number})", referer=TOPIC_URL)
        visited.add(number)
        pending.update(page.topic_links())
        if number > latest_number:
            latest, latest_number = page, number


def post_to_topic(
    tag: str, message: str, username: str, password: str, *, check_access: bool = False,
) -> None:
    try:
        context = _forum_ssl_context()
    except (OSError, ValueError) as exc:
        raise _request_failure("QC Salon", "load TLS chain", exc) from None
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),
        urllib.request.HTTPSHandler(context=context),
        _SameSiteRedirect(),
    )
    _request(opener, LOGIN_URL, stage="sign-in request", referer=TOPIC_URL, fields={
        "login": username,
        "password": password,
        "referer": urllib.parse.urlsplit(TOPIC_URL).path,
    })
    marker = release_url(tag)
    already_posted, latest = _find_announcement(opener, marker, stage="read topic before posting")
    if already_posted and not check_access:
        print(f"QC Salon already contains the announcement for {tag}; nothing posted.")
        return

    form = next((form for form in latest.forms if form["method"] == "post" and form["textarea"]), None)
    if form is None:
        raise RuntimeError("Could not find the reply form on the QC Salon topic.")
    if not _same_site(form["action"]):
        raise RuntimeError("QC Salon reply form points outside the expected HTTPS origin.")
    if check_access:
        print(f"QC Salon sign-in and reply form verified for {tag}. No message submitted.")
        return
    fields = {**form["fields"], form["textarea"]: message}
    result = _request(opener, form["action"], stage="submit reply", referer=latest.url, fields=fields)
    if marker not in result.links and not _find_announcement(opener, marker, stage="verify posted reply")[0]:
        raise RuntimeError("QC Salon did not confirm the reply. Check the topic before retrying.")
    print(f"QC Salon announcement verified for {tag}: {TOPIC_URL}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--notes-file", type=Path, default=release.ROOT / "release-notes.txt")
    source.add_argument("--published-release", action="store_true",
                        help="Read notes from the already published Android release on GitHub.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="Print only; do not sign in or post.")
    mode.add_argument("--check-access", action="store_true", help="Verify sign-in and reply form without posting.")
    args = parser.parse_args()
    username = os.getenv("QCSALON_USERNAME", "").strip()
    password = os.getenv("QCSALON_PASSWORD", "")
    if not args.dry_run and (not username or not password):
        if args.check_access:
            parser.exit(1, "Configure QCSALON_USERNAME and QCSALON_PASSWORD in GitHub Actions.\n")
        print("QCSALON_USERNAME or QCSALON_PASSWORD not set; skipping QC Salon notification.")
        return
    try:
        notes = published_notes(args.tag) if args.published_release else args.notes_file.read_text(encoding="utf-8")
        message = format_message(args.tag, notes)
    except RuntimeError as exc:
        parser.exit(1, f"{exc}\n")
    except (OSError, ValueError):
        parser.exit(1, "Invalid tag or unreadable or empty release notes.\n")
    if args.dry_run:
        print(message)
        return
    try:
        post_to_topic(args.tag, message, username, password, check_access=args.check_access)
    except RuntimeError as exc:
        parser.exit(1, f"{exc}\n")
    except (OSError, ValueError):
        # Do not log HTTP bodies, credential fields, or cookie values.
        parser.exit(1, "QC Salon request failed. Check the topic and the configured account.\n")


if __name__ == "__main__":
    main()

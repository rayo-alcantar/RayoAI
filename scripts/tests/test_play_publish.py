import json
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from scripts import distribution as dist, play_publish as play, release
from test_distribution import paired_fixture, SHA, TAG


class FakeAPI:
    def __init__(self, bundles=None, tracks=None, failure=None, bad_upload=False):
        self.bundles = bundles or []
        self.tracks = tracks or []
        self.failure = failure
        self.bad_upload = bad_upload
        self.calls = []
        self.counter = 0

    def request(self, method, path, **kwargs):
        self.calls.append((method, path, kwargs))
        if self.failure and self.failure(method, path):
            raise RuntimeError("simulated remote failure")
        if method == "POST" and path == "edits":
            self.counter += 1
            return {"id": str(self.counter)}
        if path.endswith(":commit") or path.endswith(":validate") or method == "DELETE":
            return {}
        if path.endswith("/tracks"):
            return {"tracks": self.tracks}
        if "/tracks/" in path:
            if method == "PUT":
                self.tracks = [kwargs["body"]]
                return kwargs["body"]
            return next(t for t in self.tracks if t["track"] == path.rsplit("/", 1)[1])
        if path.endswith("/bundles"):
            if method == "GET":
                return {"bundles": self.bundles}
            bundle = {"versionCode": 47, "sha256": release.sha256(kwargs["file"])}
            if self.bad_upload:
                bundle["sha256"] = "0" * 64
            self.bundles.append(bundle)
            return bundle
        raise AssertionError((method, path, kwargs))


def track(code="46", status="completed"):
    return {"track": "production", "releases": [{"versionCodes": [code], "status": status}]}


class PublishTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = paired_fixture(Path(self.temp.name))
        self.data = dist.verify_set(self.output, TAG, SHA)

    def publish(self, api, status="completed", review=True):
        return play.publish(api, self.output, TAG, SHA, "production", status, review)

    def remote_bundle(self):
        return {"versionCode": 47, "sha256": self.data["files"][f"play/{dist.AAB_NAME}"]}

    def test_complete_upload_commit_and_fresh_remote_verification(self):
        api = FakeAPI(tracks=[track()])
        result = self.publish(api)
        self.assertEqual(result["result"], "accepted_in_track")
        self.assertEqual(api.counter, 2)  # Verification is a new remote snapshot.
        commit = next(c for c in api.calls if c[1].endswith(":commit"))
        self.assertEqual(commit[2]["params"], {"changesNotSentForReview": "false", "changesInReviewBehavior": "ERROR_IF_IN_REVIEW"})
        self.assertEqual(len([c for c in api.calls if c[2].get("file")]), 1)

    def test_draft_preserves_current_completed_release(self):
        api = FakeAPI(tracks=[track()])
        self.publish(api, "draft", False)
        statuses = [r["status"] for r in api.tracks[0]["releases"]]
        self.assertEqual(statuses, ["completed", "draft"])
        commit = next(c for c in api.calls if c[1].endswith(":commit"))
        self.assertEqual(commit[2]["params"]["changesNotSentForReview"], "true")

    def test_retry_is_noop_and_does_not_reupload_or_commit(self):
        api = FakeAPI(bundles=[self.remote_bundle()], tracks=[track("47")])
        self.assertEqual(self.publish(api)["result"], "already_in_track")
        self.assertFalse(any(c[1].endswith(":commit") or c[0] == "PUT" or c[2].get("file") for c in api.calls))

    def test_existing_same_bundle_can_promote_draft_without_upload(self):
        api = FakeAPI(bundles=[self.remote_bundle()], tracks=[track("47", "draft")])
        self.publish(api)
        self.assertFalse(any(c[2].get("file") for c in api.calls))
        self.assertTrue(any(c[1].endswith(":commit") for c in api.calls))

    def test_same_code_different_bytes_rejected_before_writes(self):
        api = FakeAPI(bundles=[{"versionCode": 47, "sha256": "different"}], tracks=[track()])
        with self.assertRaisesRegex(ValueError, "different AAB"):
            self.publish(api)
        self.assertFalse(any(c[0] == "PUT" or c[2].get("file") for c in api.calls))

    def test_other_drafts_rollouts_or_newer_versions_are_preserved(self):
        for current in (track("46", "draft"), track("46", "inProgress"), track("46", "halted"), track("48")):
            api = FakeAPI(tracks=[current])
            with self.subTest(current=current), self.assertRaises(ValueError):
                self.publish(api)
            self.assertFalse(any(c[0] == "PUT" or c[2].get("file") for c in api.calls))

    def test_newer_bundle_in_other_track_also_stops_old_publication(self):
        api = FakeAPI(bundles=[{"versionCode": 48}], tracks=[track()])
        with self.assertRaisesRegex(ValueError, "newer Play bundle"):
            self.publish(api)

    def test_existing_identical_bundle_can_promote_while_newer_beta_exists(self):
        api = FakeAPI(bundles=[self.remote_bundle(), {"versionCode": 48}], tracks=[track()])
        self.assertEqual(self.publish(api)["result"], "accepted_in_track")
        self.assertFalse(any(c[2].get("file") for c in api.calls))

    def test_mismatched_uploaded_bundle_never_updates_track(self):
        api = FakeAPI(tracks=[track()], bad_upload=True)
        with self.assertRaisesRegex(ValueError, "different AAB"):
            self.publish(api)
        self.assertFalse(any(c[0] == "PUT" for c in api.calls))

    def test_validation_or_review_failure_is_not_retried(self):
        for suffix in (":validate", ":commit"):
            api = FakeAPI(tracks=[track()], failure=lambda method, path: path.endswith(suffix))
            with self.subTest(suffix=suffix), self.assertRaises(RuntimeError):
                self.publish(api)
            self.assertEqual(len([c for c in api.calls if c[1].endswith(suffix)]), 1)
            self.assertTrue(any(c[0] == "DELETE" for c in api.calls))

    def test_inspection_does_not_upload_update_or_commit(self):
        api = FakeAPI(tracks=[track()], bundles=[{"versionCode": 46}])
        self.assertEqual(play.inspect(api)["highest_bundle_version_code"], 46)
        self.assertFalse(any(c[0] == "PUT" or c[2].get("file") or c[1].endswith(":commit") for c in api.calls))
        self.assertEqual(api.calls[-1][0], "DELETE")

    def test_inspection_discards_edit_after_each_read_failure(self):
        for suffix in ("/tracks", "/bundles"):
            api = FakeAPI(failure=lambda method, path: method == "GET" and path.endswith(suffix))
            with self.subTest(suffix=suffix), self.assertRaisesRegex(RuntimeError, "simulated remote failure"):
                play.inspect(api)
            self.assertEqual(api.calls[-1][:2], ("DELETE", "edits/1"))

    def test_inspection_cli_never_uses_artifacts_or_publication(self):
        api = FakeAPI(bundles=[{"versionCode": 9}, {"versionCode": 45}], tracks=[track("45")])
        output = StringIO()
        with patch.object(play, "PlayAPI", return_value=api), \
                patch.object(play, "publish", side_effect=AssertionError("Publication must not run")), \
                patch.object(dist, "verify_set", side_effect=AssertionError("Artifacts must not be read")), \
                patch.dict("os.environ", {"GITHUB_STEP_SUMMARY": ""}), \
                patch("sys.argv", ["play_publish.py", "inspect"]), redirect_stdout(output):
            play.main()
        result = json.loads(output.getvalue())
        self.assertEqual(result["highest_bundle_version_code"], 45)
        self.assertEqual([call[:2] for call in api.calls], [
            ("POST", "edits"), ("GET", "edits/1/tracks"), ("GET", "edits/1/bundles"), ("DELETE", "edits/1")])

    def test_cannot_demote_or_send_beta_to_production(self):
        with self.assertRaises(ValueError):
            play.track_update(track("47"), self.data, "production", "draft", [])
        with self.assertRaises(ValueError):
            play.track_update({}, self.data | {"channel": "beta"}, "production", "completed", [])

    def test_tampered_artifact_fails_before_auth_requests(self):
        (self.output / "play" / dist.AAB_NAME).write_bytes(b"tampered")
        api = FakeAPI()
        with self.assertRaises(ValueError):
            self.publish(api)
        self.assertEqual(api.calls, [])


class HTTPTransportTest(unittest.TestCase):
    def test_real_local_http_streaming_and_no_redirect_token_leak(self):
        calls = []

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_POST(self):
                body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
                calls.append((self.path, dict(self.headers), body))
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b'{"ok":true}')

            def do_GET(self):
                calls.append((self.path, dict(self.headers), b""))
                if "redirect" in self.path:
                    self.send_response(302)
                    self.send_header("Location", "/must-not-receive-token")
                else:
                    self.send_response(403)
                self.end_headers()
                self.wfile.write(b'{"error":{"message":"fake-token rejected"}}')

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        url = f"http://127.0.0.1:{server.server_port}/apps/"
        api = play.PlayAPI("fake-token")
        with patch.object(play, "API_ROOT", url), patch.object(play, "UPLOAD_ROOT", url), tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bundle.aab"
            path.write_bytes(b"binary\x00payload" * 10000)
            self.assertEqual(api.request("POST", "edits/1/bundles", file=path, params={"uploadType": "media"}), {"ok": True})
            self.assertEqual(calls[0][2], path.read_bytes())
            self.assertEqual(calls[0][1]["Authorization"], "Bearer fake-token")
            with self.assertRaisesRegex(RuntimeError, "REDACTED") as caught:
                api.request("GET", "forbidden")
            self.assertNotIn("fake-token", str(caught.exception))
            with self.assertRaises(RuntimeError):
                api.request("GET", "redirect")
            self.assertFalse(any("must-not-receive-token" in c[0] for c in calls))


if __name__ == "__main__":
    unittest.main()

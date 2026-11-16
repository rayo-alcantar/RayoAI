import contextlib
import io
import json
import ssl
import unittest
from unittest.mock import patch
import urllib.error
import urllib.parse

from scripts import qcsalon_publish as qc


TAG = "3.3.12-stable"
LATEST = f"{qc.BASE_URL}/es/forum32/topic147452p5"
MARKER = f'<a href="{qc.release_url(TAG)}">Release</a>'
FORM = (
    '<form action="/es/forum32/topic147452" method="post">'
    '<input type="hidden" name="token" value="synthetic">'
    '<textarea name="content"></textarea></form>'
)


@contextlib.contextmanager
def response(url, html):
    class Page(io.BytesIO):
        def geturl(self):
            return url

    with Page(html.encode()) as page:
        yield page


class QCSalonPublishTest(unittest.TestCase):
    def test_format_message_uses_only_android_assets(self):
        msg = qc.format_message(TAG, "Notas de prueba.")
        self.assertIn("RayoAI para Android: 3.3.12 (estable)", msg)
        self.assertIn("Notas de prueba.", msg)
        self.assertIn(f"/{TAG}/app-github-release.apk", msg)
        self.assertIn(qc.release_url(TAG), msg)
        self.assertNotIn("RayoAI.zip", msg)

    def test_published_notes_require_uploaded_apk_and_remove_build_marker(self):
        public = {
            "tag_name": TAG,
            "draft": False,
            "prerelease": False,
            "assets": [{"name": "app-github-release.apk", "state": "uploaded"}],
            "body": f"Notas de prueba.\r\n\r\nRayoAI build: {TAG} / {'a' * 40}",
        }
        with patch.object(qc.urllib.request, "urlopen", return_value=io.BytesIO(json.dumps(public).encode())):
            self.assertEqual(qc.published_notes(TAG), "Notas de prueba.")
        public["assets"] = []
        with patch.object(qc.urllib.request, "urlopen", return_value=io.BytesIO(json.dumps(public).encode())):
            with self.assertRaisesRegex(RuntimeError, "missing its Android APK"):
                qc.published_notes(TAG)

    def test_tls_still_requires_hostname_and_a_trusted_root(self):
        tls = qc._forum_ssl_context()
        self.assertTrue(tls.check_hostname)
        self.assertEqual(tls.verify_mode, ssl.CERT_REQUIRED)
        self.assertFalse(tls.verify_flags & ssl.VERIFY_X509_PARTIAL_CHAIN)

    def test_main_skips_when_credentials_empty(self):
        with patch.dict("os.environ", {"QCSALON_USERNAME": "", "QCSALON_PASSWORD": ""}):
            with patch("sys.argv", ["qcsalon_publish", "--tag", TAG]), contextlib.redirect_stdout(io.StringIO()) as out:
                qc.main()
        self.assertIn("skipping QC Salon notification", out.getvalue())

    def test_login_http_failure_has_stage_but_not_sensitive_details(self):
        error = urllib.error.HTTPError(qc.LOGIN_URL, 403, "synthetic private detail", {}, None)
        with patch.object(qc.urllib.request, "build_opener") as factory:
            factory.return_value.open.side_effect = error
            with self.assertRaisesRegex(RuntimeError, r"sign-in request failed \(HTTP 403\)\.") as ctx:
                qc.post_to_topic(TAG, "test", "fake-user", "fake-password")
        self.assertNotIn("synthetic private detail", str(ctx.exception))

    def test_login_form_means_signin_failed(self):
        with patch.object(qc.urllib.request, "build_opener") as factory:
            factory.return_value.open.side_effect = [
                response(qc.LOGIN_URL, ""),
                response(qc.TOPIC_URL, '<form id="loginform"></form>'),
            ]
            with self.assertRaisesRegex(RuntimeError, "sign-in did not succeed"):
                qc.post_to_topic(TAG, "test", "fake-user", "fake-password")

    def test_access_check_does_not_submit_even_with_existing_announcement(self):
        with patch.object(qc.urllib.request, "build_opener") as factory, contextlib.redirect_stdout(io.StringIO()) as out:
            factory.return_value.open.side_effect = [
                response(qc.LOGIN_URL, ""),
                response(qc.TOPIC_URL, FORM + MARKER),
            ]
            qc.post_to_topic(TAG, "test", "fake-user", "fake-password", check_access=True)
        self.assertEqual(factory.return_value.open.call_count, 2)
        self.assertIn("No message submitted", out.getvalue())

    def test_posts_after_scanning_pages_and_verifies_exact_release_link(self):
        with patch.object(qc.urllib.request, "build_opener") as factory, contextlib.redirect_stdout(io.StringIO()):
            factory.return_value.open.side_effect = [
                response(qc.LOGIN_URL, ""),
                response(qc.TOPIC_URL, f'<a href="{LATEST}">5</a>'),
                response(LATEST, FORM),
                response(LATEST, MARKER),
            ]
            qc.post_to_topic(TAG, "Notas de prueba", "fake-user", "fake-password")
        op = factory.return_value.open
        self.assertEqual(op.call_count, 4)
        post = op.call_args.args[0]
        self.assertEqual(post.full_url, f"{qc.BASE_URL}/es/forum32/topic147452")
        self.assertEqual(urllib.parse.parse_qs(post.data.decode()), {
            "token": ["synthetic"], "content": ["Notas de prueba"],
        })

    def test_existing_exact_link_on_later_page_does_not_duplicate(self):
        with patch.object(qc.urllib.request, "build_opener") as factory, contextlib.redirect_stdout(io.StringIO()) as out:
            factory.return_value.open.side_effect = [
                response(qc.LOGIN_URL, ""),
                response(qc.TOPIC_URL, f'<a href="{LATEST}">5</a>'),
                response(LATEST, FORM + MARKER),
            ]
            qc.post_to_topic(TAG, "test", "fake-user", "fake-password")
        self.assertEqual(factory.return_value.open.call_count, 3)
        self.assertIn("nothing posted", out.getvalue())

    def test_form_on_another_origin_is_not_submitted(self):
        malicious = FORM.replace('/es/forum32/topic147452', 'https://example.invalid/reply')
        with patch.object(qc.urllib.request, "build_opener") as factory:
            factory.return_value.open.side_effect = [
                response(qc.LOGIN_URL, ""),
                response(qc.TOPIC_URL, malicious),
            ]
            with self.assertRaisesRegex(RuntimeError, "outside the expected HTTPS origin"):
                qc.post_to_topic(TAG, "test", "fake-user", "fake-password")
        self.assertEqual(factory.return_value.open.call_count, 2)


if __name__ == "__main__":
    unittest.main()

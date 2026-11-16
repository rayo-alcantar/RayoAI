import io
from pathlib import Path
import unittest
from unittest.mock import MagicMock, patch
import urllib.parse
import urllib.request

from scripts import qcsalon_publish


class QCSalonPublishTest(unittest.TestCase):
    def test_format_message(self):
        msg = qcsalon_publish.format_message("3.3.12-stable", "Notas de prueba.")
        self.assertIn("RayoAI 3.3.12 (stable)", msg)
        self.assertIn("Notas de prueba.", msg)
        self.assertIn("https://github.com/rayo-alcantar/rayoai_for_android/releases/download/3.3.12-stable/app-github-release.apk", msg)
        self.assertIn("https://github.com/rayo-alcantar/rayoai_for_android/releases/tag/3.3.12-stable", msg)

    def test_form_parser(self):
        html = """
        <html>
            <body>
                <form action="/es/login" method="post" id="loginform">
                    <input name="login" value="" />
                </form>
                <form action="/es/reply" method="post" id="replyform">
                    <input type="hidden" name="token" value="secret123" />
                    <textarea name="body"></textarea>
                </form>
            </body>
        </html>
        """
        parser = qcsalon_publish.FormParser()
        parser.feed(html)
        self.assertEqual(len(parser.forms), 2)
        reply = next(f for f in parser.forms if f.get("textarea"))
        self.assertEqual(reply["action"], "/es/reply")
        self.assertEqual(reply["inputs"], {"token": "secret123"})
        self.assertEqual(reply["textarea"], "body")

    def test_main_skips_when_credentials_empty(self):
        with patch.dict("os.environ", {"QCSALON_USERNAME": "", "QCSALON_PASSWORD": ""}), \
             patch("sys.stdout", new=io.StringIO()) as fake_out, \
             patch("sys.argv", ["qcsalon_publish", "--tag", "3.3.12-stable"]):
            qcsalon_publish.main()
            self.assertIn("skipping QC Salon notification", fake_out.getvalue())

    def test_post_to_topic_login_failed(self):
        mock_opener = MagicMock()
        mock_resp_login = MagicMock()
        mock_resp_topic = MagicMock()
        mock_resp_topic.read.return_value = b'<html><form id="loginform"></form></html>'
        mock_resp_topic.__enter__.return_value = mock_resp_topic
        mock_opener.open.side_effect = [mock_resp_login, mock_resp_topic]

        with self.assertRaises(RuntimeError) as ctx:
            qcsalon_publish.post_to_topic(
                "https://qcsalon.net/es/forum5000000/topic147452",
                "hello",
                "testuser",
                "wrongpass",
                opener=mock_opener,
            )
        self.assertIn("QC Salon login failed", str(ctx.exception))

    def test_post_to_topic_missing_reply_form(self):
        mock_opener = MagicMock()
        mock_resp_login = MagicMock()
        mock_resp_topic = MagicMock()
        mock_resp_topic.read.return_value = b'<html><body><h1>Locked topic</h1></body></html>'
        mock_resp_topic.__enter__.return_value = mock_resp_topic
        mock_opener.open.side_effect = [mock_resp_login, mock_resp_topic]

        with self.assertRaises(RuntimeError) as ctx:
            qcsalon_publish.post_to_topic(
                "https://qcsalon.net/es/forum5000000/topic147452",
                "hello",
                "testuser",
                "secretpass",
                opener=mock_opener,
            )
        self.assertIn("Could not find reply form", str(ctx.exception))

    def test_post_to_topic_dry_run(self):
        mock_opener = MagicMock()
        mock_resp_login = MagicMock()
        mock_resp_topic = MagicMock()
        topic_html = (
            b'<html><body><h1>Responder al tema</h1>'
            b'<form action="/es/forum32/topic147452" method="post">'
            b'<textarea name="content"></textarea>'
            b'</form></body></html>'
        )
        mock_resp_topic.read.return_value = topic_html
        mock_resp_topic.__enter__.return_value = mock_resp_topic

        mock_opener.open.side_effect = [mock_resp_login, mock_resp_topic]

        with patch("sys.stdout", new=io.StringIO()) as fake_out:
            qcsalon_publish.post_to_topic(
                "https://qcsalon.net/es/forum5000000/topic147452",
                "Mensaje de prueba",
                "testuser",
                "secretpass",
                dry_run=True,
                opener=mock_opener,
            )
            self.assertIn("[DRY-RUN]", fake_out.getvalue())
        # In dry-run, only login and topic get are called, NO post!
        self.assertEqual(mock_opener.open.call_count, 2)

    def test_post_to_topic_success(self):
        mock_opener = MagicMock()
        mock_resp_login = MagicMock()
        mock_resp_topic = MagicMock()
        topic_html = (
            b'<html><body><h1>Responder al tema</h1>'
            b'<form action="/es/forum32/topic147452" method="post">'
            b'<input type="hidden" name="token" value="csrf_val"/>'
            b'<textarea name="content"></textarea>'
            b'</form></body></html>'
        )
        mock_resp_topic.read.return_value = topic_html
        mock_resp_topic.__enter__.return_value = mock_resp_topic

        mock_resp_post = MagicMock()
        mock_resp_post.status = 200
        mock_resp_post.__enter__.return_value = mock_resp_post

        mock_opener.open.side_effect = [mock_resp_login, mock_resp_topic, mock_resp_post]

        qcsalon_publish.post_to_topic(
            "https://qcsalon.net/es/forum5000000/topic147452",
            "Mensaje de prueba",
            "testuser",
            "secretpass",
            opener=mock_opener,
        )

        self.assertEqual(mock_opener.open.call_count, 3)
        post_req = mock_opener.open.call_args_list[2][0][0]
        self.assertEqual(post_req.full_url, "https://qcsalon.net/es/forum32/topic147452")
        sent_data = urllib.parse.parse_qs(post_req.data.decode("utf-8"))
        self.assertEqual(sent_data["token"], ["csrf_val"])
        self.assertEqual(sent_data["content"], ["Mensaje de prueba"])


if __name__ == "__main__":
    unittest.main()

import json
from contextlib import redirect_stdout
from io import StringIO
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts import distribution as dist, release

SHA = "a" * 40
TAG = "3.3.12-stable"


def paired_fixture(root: Path) -> Path:
    """A clearly synthetic artifact: binary signature checks are tested separately."""
    (root / "app").mkdir(parents=True)
    (root / "app/build.gradle.kts").write_text('versionName = "3.3.12"\nversionCode = 47', encoding="utf-8")
    (root / "release-notes.txt").write_text("RayoAI 3.3.12\nTest notes", encoding="utf-8")
    for language in ("values", "values-en"):
        path = root / "app/src/main/res" / language
        path.mkdir(parents=True)
        (path / "strings.xml").write_text('<resources><string name="test">test</string></resources>')
    (root / "play-release-notes").mkdir()
    (root / "play-release-notes/es-419.txt").write_text("Mejoras y correcciones.", encoding="utf-8")
    apk = root / "app/build/outputs/apk/github/release/app-github-release.apk"
    apk.parent.mkdir(parents=True)
    with zipfile.ZipFile(apk, "w") as archive:
        for name in ("AndroidManifest.xml", "classes.dex", "resources.arsc", "META-INF/TEST.RSA"):
            archive.writestr(name, b"synthetic, not a real signed APK")
    aab = root / "app/build/outputs/bundle/playRelease/app-play-release.aab"
    aab.parent.mkdir(parents=True)
    aab.write_bytes(b"synthetic bundle")
    output = root / "out"
    identity = {"package_name": "com.rayoai", "version": "3.3.12", "version_code": 47,
                "upload_certificate_sha256": "b" * 64}
    with patch.object(dist, "inspect_binaries", return_value=identity), \
            patch.object(release, "run", side_effect=lambda *args: SHA if "rev-parse" in args else ""), redirect_stdout(StringIO()):
        dist.package_set(TAG, root / "tools", root / "tool.jar", output, root)
    return output


class InspectionModeTest(unittest.TestCase):
    def request(self, **overrides):
        dist.validate_inspection_request(**(dict(destination="play", tag="", source_sha=SHA,
            caller_sha=SHA, run_id="", track="internal", status="draft", send_for_review="false") | overrides))

    def test_inspection_accepts_only_the_non_publishing_request(self):
        self.request()

    def test_inspection_rejects_publication_or_other_source_inputs(self):
        for changes in ({"destination": "github"}, {"tag": TAG}, {"run_id": "1"},
                        {"track": "production"}, {"status": "completed"}, {"send_for_review": "true"},
                        {"caller_sha": "b" * 40}, {"source_sha": "", "caller_sha": ""},
                        {"source_sha": "dev", "caller_sha": "dev"}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                self.request(**changes)


class PlanTest(unittest.TestCase):
    def test_all_destination_order_combinations(self):
        for destination in ("none", "github", "play", "both"):
            for order in ("parallel", "github-first", "play-first"):
                with self.subTest(destination=destination, order=order):
                    plan = dist.publication_plan(TAG, destination, order, "auto", "completed")
                    self.assertEqual(plan["play_track"], "production")
                    expected = {"none": set(), "github": {"github"}, "play": {"play"}, "both": {"github", "play"}}[destination]
                    self.assertEqual(set(plan["first"] + plan["second"]), expected)
                    if destination == "both" and order != "parallel":
                        self.assertEqual(plan["first"], [order.removesuffix("-first")])
                        self.assertEqual(len(plan["second"]), 1)

    def test_beta_defaults_to_internal(self):
        self.assertEqual(dist.publication_plan("3.3.12-beta", "both", "parallel", "auto", "draft")["play_track"], "internal")

    def test_invalid_inputs_fail_closed(self):
        for kwargs in ({"destinations": "all"}, {"order": "random"}, {"track": "private"},
                       {"status": "inProgress"}, {"source_ref": "dev"}, {"run_id": "1; rm"},
                       {"run_id": "0"}, {"run_id": "１２"}, {"tag": "v3.3.12-stable"}):
            args = dict(tag=TAG, destinations="both", order="parallel", track="auto", status="completed") | kwargs
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                dist.publication_plan(**args)
        with self.assertRaises(ValueError):
            dist.publication_plan("3.3.12-beta", "play", "parallel", "production", "completed")

    def test_source_ref_only_fresh_build(self):
        self.assertEqual(dist.publication_plan(TAG, "none", "parallel", "auto", "draft", "dev")["first"], [])
        with self.assertRaises(ValueError):
            dist.publication_plan(TAG, "none", "parallel", "auto", "draft", "dev", "123")


class ArtifactTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.output = paired_fixture(self.root)

    def test_round_trip_public_contract_and_pair(self):
        data = dist.verify_set(self.output, TAG, SHA)
        self.assertEqual(data["version_code"], 47)
        self.assertEqual(len(data["files"]), 6)
        public = release.verify_artifacts(self.output / "github", TAG, SHA)
        self.assertEqual(public["version_code"], 47)
        self.assertNotIn(dist.AAB_NAME, public["assets"])

    def test_each_file_tampering_is_detected(self):
        data = dist.verify_set(self.output, TAG, SHA)
        for name in data["files"]:
            path = self.output / name
            original = path.read_bytes()
            path.write_bytes(original + b"tamper")
            with self.subTest(name=name), self.assertRaises(ValueError):
                dist.verify_set(self.output, TAG, SHA)
            path.write_bytes(original)

    def test_unexpected_file_and_wrong_identity(self):
        extra = self.output / "secret.json"
        extra.write_text("must never be published")
        with self.assertRaises(ValueError):
            dist.verify_set(self.output, TAG, SHA)
        extra.unlink()
        for tag, sha in (("3.3.13-stable", SHA), (TAG, "b" * 40), (TAG, "invalid")):
            with self.subTest(tag=tag, sha=sha), self.assertRaises(ValueError):
                dist.verify_set(self.output, tag, sha)

    def test_pair_must_agree_on_version_code(self):
        path = self.output / dist.SET_NAME
        data = json.loads(path.read_text())
        data["version_code"] = 48
        path.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "versionCode differ"):
            dist.verify_set(self.output, TAG, SHA)

    def test_never_overwrite_output(self):
        identity = {"package_name": "com.rayoai", "version": "3.3.12", "version_code": 47}
        with patch.object(release, "run", return_value=""), patch.object(dist, "inspect_binaries", return_value=identity), self.assertRaisesRegex(ValueError, "not empty"):
            dist.package_set(TAG, self.root, self.root / "jar", self.output, self.root)

    def test_stale_binary_identity_rejected(self):
        for identity in ({"package_name": "wrong.app", "version": "3.3.12", "version_code": 47},
                         {"package_name": "com.rayoai", "version": "3.3.11", "version_code": 47},
                         {"package_name": "com.rayoai", "version": "3.3.12", "version_code": 46}):
            with patch.object(release, "run", return_value=""), patch.object(dist, "inspect_binaries", return_value=identity), self.assertRaises(ValueError):
                dist.package_set(TAG, self.root, self.root / "jar", self.root / "another", self.root)

    def test_dirty_source_cannot_be_labelled_as_a_publishable_pair(self):
        with patch.object(release, "run", return_value=" M app/source.kt"), self.assertRaisesRegex(ValueError, "clean committed"):
            dist.package_set(TAG, self.root, self.root / "jar", self.root / "another", self.root)

    def test_notes_limits(self):
        path = self.root / "play-release-notes/es-419.txt"
        for value in ("", "x" * 501):
            path.write_text(value)
            with self.assertRaises(ValueError):
                dist.play_notes(self.root)
        path.write_text("x" * 500)
        self.assertEqual(len(dist.play_notes(self.root)[0]["text"]), 500)

    @patch.dict(os.environ, {"GH_TOKEN": "synthetic-token"})
    def test_public_github_retry_is_idempotent_and_never_overwrites(self):
        output = self.output / "github"
        assets = [{"name": name, "digest": "sha256:" + release.sha256(output / name)}
                  for name in ("app-github-release.apk", "SHA256SUMS.txt", "release.json")]
        existing = {"tag_name": TAG, "draft": False, "prerelease": False, "assets": assets,
                    "body": f"RayoAI build: {TAG} / {SHA}"}
        responses = [{"private": False, "archived": False, "default_branch": "main"}, [[existing]]]
        with patch.object(release, "gh_json", side_effect=responses), patch.object(release, "run") as run, redirect_stdout(StringIO()):
            release.publish(TAG, SHA, output)
            run.assert_not_called()
        existing["assets"][0]["digest"] = "sha256:wrong"
        with patch.object(release, "gh_json", side_effect=responses), patch.object(release, "run") as run:
            with self.assertRaisesRegex(ValueError, "never overwrite"):
                release.publish(TAG, SHA, output)
            run.assert_not_called()


class ProvenanceTest(unittest.TestCase):
    def responses(self, sha=SHA, workflow=".github/workflows/release.yml", conclusion="success", expired=False):
        return [{"head_sha": sha, "path": workflow, "event": "push"},
                [{"jobs": [{"name": "Build paired artifacts", "conclusion": conclusion}]}],
                [{"artifacts": [{"name": dist.ARTIFACT_NAME, "expired": expired}]}]]

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": dist.SOURCE_REPO})
    def test_accept_successful_build_despite_later_publish_failure(self):
        with patch.object(release, "gh_json", side_effect=self.responses()):
            dist.verify_run("123", SHA)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": dist.SOURCE_REPO})
    def test_reject_mismatched_source_workflow_failed_build_or_expired_artifact(self):
        for kwargs in ({"sha": "b" * 40}, {"workflow": "arbitrary.yml"}, {"conclusion": "failure"}, {"expired": True}):
            with self.subTest(kwargs=kwargs), patch.object(release, "gh_json", side_effect=self.responses(**kwargs)), self.assertRaises(ValueError):
                dist.verify_run("123", SHA)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": "other/repo"})
    def test_reject_other_repository(self):
        with self.assertRaises(ValueError):
            dist.verify_run("123", SHA)

    def test_dispatch_requires_artifact_and_explicit_destination(self):
        with patch.object(release, "run") as run:
            for run_id, destinations in (("", "both"), ("123", "none")):
                with self.assertRaises(ValueError):
                    dist.dispatch(TAG, run_id, destinations, "parallel", "auto", "draft", False)
            run.assert_not_called()

    def test_dispatch_queues_exact_artifact_and_reports_not_deployed(self):
        with patch.object(release, "gh_json", return_value={"object": {"type": "commit", "sha": SHA}}), \
                patch.object(dist, "verify_run") as verify, patch.object(release, "run") as run, redirect_stdout(StringIO()):
            dist.dispatch(TAG, "123", "both", "play-first", "internal", "completed", True)
            verify.assert_called_once_with("123", SHA)
            self.assertIn("artifact_run_id=123", run.call_args.args)
            self.assertIn("publication_order=play-first", run.call_args.args)
            self.assertIn("send_for_review=true", run.call_args.args)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": dist.SOURCE_REPO, "GITHUB_RUN_ID": "456"})
    def test_origin_new_source_uses_current_run(self):
        with patch.object(release, "gh_json", side_effect=[[{"artifacts": []}], [{"workflow_runs": []}]]):
            self.assertEqual(dist.artifact_origin(SHA), {"run_id": "456", "reuse": "false"})

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": dist.SOURCE_REPO, "GITHUB_RUN_ID": "456"})
    def test_origin_reuses_original_pair_automatically(self):
        artifact = {"name": dist.ARTIFACT_NAME, "workflow_run": {"id": 123, "head_sha": SHA}}
        with patch.object(release, "gh_json", return_value=[{"artifacts": [artifact]}]), patch.object(dist, "verify_run") as verify:
            self.assertEqual(dist.artifact_origin(SHA), {"run_id": "123", "reuse": "true"})
            verify.assert_called_once_with("123", SHA)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": dist.SOURCE_REPO})
    def test_origin_explicit_id_is_verified(self):
        with patch.object(dist, "verify_run") as verify:
            self.assertEqual(dist.artifact_origin(SHA, "123"), {"run_id": "123", "reuse": "true"})
            verify.assert_called_once_with("123", SHA)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": dist.SOURCE_REPO, "GITHUB_RUN_ID": "456"})
    def test_missing_original_artifact_never_silently_rebuilds_successful_source(self):
        responses = [[{"artifacts": []}], [{"workflow_runs": [{"id": 123}]}],
                     [{"jobs": [{"name": "Build paired artifacts", "conclusion": "success"}]}]]
        with patch.object(release, "gh_json", side_effect=responses), self.assertRaisesRegex(ValueError, "artifact is missing"):
            dist.artifact_origin(SHA)


class BinaryVerificationTest(unittest.TestCase):
    def output(self, mismatch=False):
        return ["package: name='com.rayoai' versionCode='47' versionName='3.3.12'",
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.rayoai" android:versionCode="47" android:versionName="3.3.12"/>',
                "Signer #1 certificate SHA-256 digest: " + "a" * 64,
                "jar verified.\nWarning: self-signed certificate",
                "SHA256: " + ":".join(["BB" if mismatch else "AA"] * 32), "Bundle is valid"]

    def test_verifies_identity_signatures_certificate_and_bundle(self):
        with patch.object(dist, "bundletool"), patch.object(dist, "tool_output", side_effect=self.output()):
            info = dist.inspect_binaries(Path("app.apk"), Path("app.aab"), Path("tools"), Path("jar"))
            self.assertEqual(info["version_code"], 47)

    def test_different_signers_rejected(self):
        with patch.object(dist, "bundletool"), patch.object(dist, "tool_output", side_effect=self.output(True)), self.assertRaises(ValueError):
            dist.inspect_binaries(Path("app.apk"), Path("app.aab"), Path("tools"), Path("jar"))

    def test_invalid_or_partially_unsigned_aab_rejected(self):
        for message in ("jar is unsigned.", "jar verified. contains unsigned entries"):
            responses = self.output()
            responses[3] = message
            with patch.object(dist, "bundletool"), patch.object(dist, "tool_output", side_effect=responses), self.assertRaises(ValueError):
                dist.inspect_binaries(Path("app.apk"), Path("app.aab"), Path("tools"), Path("jar"))

    def test_bundletool_hash_check(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "tool.jar"
            path.write_bytes(b"not the pinned tool")
            with self.assertRaisesRegex(ValueError, "checksum"):
                dist.bundletool(path)


if __name__ == "__main__":
    unittest.main()

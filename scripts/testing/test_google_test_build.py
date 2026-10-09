#!/usr/bin/env python3
"""Adversarial checks for the operator's source and public APK identity boundary."""
from __future__ import annotations

import argparse
import base64
import hashlib
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("google_test_build", Path(__file__).with_name("prepare-google-test-build.py"))
build = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(build)


class TestBuildProvenanceTest(unittest.TestCase):
    def test_real_git_source_freeze_rejects_other_commits_and_uncommitted_inputs(self) -> None:
        with tempfile.TemporaryDirectory(prefix="goat-test-provenance-") as temporary:
            root = Path(temporary)
            def git(*args: str) -> str:
                return subprocess.run(["git", *args], cwd=root, check=True, capture_output=True, text=True).stdout.strip()
            git("init", "--quiet", "--initial-branch=operator-test")
            git("remote", "add", "origin", "https://github.com/Vanguduza/goat-app.git")
            (root / "input.kt").write_text("class Original\n")
            git("add", "input.kt")
            git("-c", "user.name=Provenance Test", "-c", "user.email=test@example.invalid", "commit", "--quiet", "-m", "fixture")
            expected = git("rev-parse", "HEAD")
            with patch.object(build, "ROOT", root):
                self.assertEqual(expected, build.source_identity(expected)["commit"])
                with self.assertRaisesRegex(ValueError, "HEAD changed"):
                    build.source_identity("0" * 40)
                (root / "untracked.kt").write_text("class Untracked\n")
                with self.assertRaisesRegex(ValueError, "source changes"):
                    build.source_identity(expected)
                (root / "untracked.kt").unlink()
                (root / "input.kt").write_text("class Changed\n")
                with self.assertRaisesRegex(ValueError, "source changes"):
                    build.source_identity(expected)

    def test_missing_private_key_is_not_generated(self) -> None:
        with tempfile.TemporaryDirectory(prefix="goat-missing-test-key-") as temporary:
            with patch.object(Path, "home", return_value=Path(temporary)), patch.object(subprocess, "run") as command:
                with self.assertRaisesRegex(ValueError, "never creates or rotates"):
                    build.debug_certificate(Path("/not-invoked/keytool"), "A" * 40)
                command.assert_not_called()
            self.assertEqual([], list(Path(temporary).iterdir()))

    def test_untrusted_apk_metadata_cannot_change_google_identity(self) -> None:
        # apksigner performs signature/X.509 verification before this metadata boundary.
        public_bytes = b"public certificate fixture only; no private key"
        sha1 = hashlib.sha1(public_bytes).hexdigest().upper()
        sha256 = hashlib.sha256(public_bytes).hexdigest().upper()
        signature = (
            f"Signer #1 certificate SHA-1 digest: {sha1}\n"
            f"Signer #1 certificate SHA-256 digest: {sha256}\n"
            "-----BEGIN CERTIFICATE-----\n" + base64.b64encode(public_bytes).decode() +
            "\n-----END CERTIFICATE-----\n"
        )
        badging = (
            "package: name='com.farmos.app' versionCode='1' versionName='test'\n"
            "sdkVersion:'26'\ntargetSdkVersion:'36'\napplication-debuggable\n"
        )
        identity, _ = build.apk_identity(signature, badging, sha1)
        self.assertEqual("com.farmos.app", identity["package"])
        for tampered_signature, tampered_manifest, pinned_sha1 in (
            (signature, badging, "B" * 40),
            (signature, badging.replace("com.farmos.app", "foreign.app"), sha1),
            (signature.replace(sha256, "C" * 64), badging, sha1),
            (signature + signature.replace("Signer #1", "Signer #2"), badging, sha1),
            (signature, badging.replace("application-debuggable\n", ""), sha1),
        ):
            with self.subTest(manifest=tampered_manifest, pinned=pinned_sha1):
                with self.assertRaises(ValueError):
                    build.apk_identity(tampered_signature, tampered_manifest, pinned_sha1)


    def test_prepare_verifies_retained_bytes_when_gradle_replaces_its_original_apk(self) -> None:
        with tempfile.TemporaryDirectory(prefix="goat-retained-apk-") as temporary:
            directory = Path(temporary)
            root, sdk, java = directory / "source", directory / "sdk", directory / "java"
            output = directory / "evidence"
            root.mkdir()
            home = directory / "home"
            home.mkdir()
            tools = sdk / "build-tools" / "37.0.0"
            for tool in (java / "bin/java", java / "bin/keytool", tools / "apksigner", tools / "aapt2"):
                tool.parent.mkdir(parents=True, exist_ok=True)
                tool.touch()
            (root / "PROJECT_CANONICAL_STATE.json").write_text(json.dumps({
                "canonical_state": {"release_blocked": True},
            }))
            (root / "PROJECT_COMPLETION_STATE.json").write_text(json.dumps({
                "programme_status": "RELEASE_BLOCKED", "coverage": {"qualified": 0},
            }))
            wrapper = root / "gradle/wrapper/gradle-wrapper.properties"
            wrapper.parent.mkdir(parents=True)
            wrapper.write_text("distributionUrl=https://example.invalid/fixture-only\n")
            original = root / "app/build/outputs/apk/debug/app-debug.apk"
            original.parent.mkdir(parents=True)
            original.write_bytes(b"stale output must not be reused")
            candidate = b"new candidate APK fixture"
            replacement = b"unrelated later build with a different identity"
            public_cert = b"public certificate fixture only; no private key"
            cert_sha1 = hashlib.sha1(public_cert).hexdigest().upper()
            signature = (
                f"Signer #1 certificate SHA-1 digest: {cert_sha1}\n"
                f"Signer #1 certificate SHA-256 digest: {hashlib.sha256(public_cert).hexdigest().upper()}\n"
                "-----BEGIN CERTIFICATE-----\n" + base64.b64encode(public_cert).decode() +
                "\n-----END CERTIFICATE-----\n"
            )
            badging = (
                "package: name='com.farmos.app' versionCode='1' versionName='test'\n"
                "sdkVersion:'26'\ntargetSdkVersion:'36'\napplication-debuggable\n"
            )
            source = {"repository": build.REPOSITORY, "commit": "a" * 40,
                      "tree": "b" * 40, "ref": "operator-test"}
            retained = output / "goat-test-aaaaaaaaaaaa.apk"
            inspected = []

            def process(command, **kwargs):
                arguments = [str(value) for value in command]
                if arguments[:2] == ["./gradlew", ":app:assembleDebug"]:
                    self.assertFalse(original.exists(), "Preparation must discard the stale APK first")
                    self.assertTrue(all(f"-P{name}=" in arguments for name in build.BLOCKED_INPUTS))
                    original.write_bytes(candidate)
                    return subprocess.CompletedProcess(command, 0, "", "")
                if arguments[0] in (str(tools / "apksigner"), str(tools / "aapt2")):
                    inspected_path = Path(arguments[-1])
                    inspected.append(inspected_path)
                    self.assertEqual(retained, inspected_path, "SDK inspection must use the retained artifact")
                    self.assertEqual(candidate, inspected_path.read_bytes())
                    if arguments[0] == str(tools / "apksigner"):
                        # A concurrent build replaces its public output after signature verification.
                        later = original.with_name("later-build.apk")
                        later.write_bytes(replacement)
                        later.replace(original)
                        return subprocess.CompletedProcess(command, 0, signature, "")
                    return subprocess.CompletedProcess(command, 0, badging, "")
                if arguments == [str(java / "bin/java"), "-version"]:
                    return subprocess.CompletedProcess(command, 0, "", "test JDK fixture")
                if arguments[:2] == ["git", "rev-parse"] and arguments[2].startswith("HEAD:PROJECT_"):
                    return subprocess.CompletedProcess(command, 0, "c" * 40, "")
                self.fail(f"Unexpected external command in isolated preparation: {arguments}")

            args = argparse.Namespace(
                expected_commit=source["commit"], expected_sha1=cert_sha1,
                output_dir=output, sdk=sdk, java_home=java, build_tools="37.0.0",
                heap_mb=1536, timeout_seconds=60,
            )
            with patch.object(build, "ROOT", root), \
                    patch.object(build, "source_identity", return_value=source), \
                    patch.object(build, "debug_certificate", return_value=public_cert), \
                    patch.object(Path, "home", return_value=home), \
                    patch.dict(build.os.environ, {}, clear=True), \
                    patch.object(subprocess, "run", side_effect=process):
                record = build.prepare(args)

            manifest = json.loads(record.read_text())
            self.assertEqual([retained, retained], inspected)
            self.assertEqual(replacement, original.read_bytes())
            self.assertEqual(candidate, retained.read_bytes())
            self.assertEqual(hashlib.sha256(candidate).hexdigest(), manifest["artifact"]["sha256"])
            self.assertEqual(len(candidate), manifest["artifact"]["size_bytes"])
            self.assertEqual(retained.name, manifest["artifact"]["filename"])
            self.assertEqual("com.farmos.app", manifest["application"]["package"])
            self.assertEqual(build.display_fingerprint(cert_sha1), manifest["application"]["certificate_sha1"])
            self.assertTrue(manifest["canonical_state"]["release_blocked"])
            self.assertEqual("NOT_GRANTED_BY_BUILD_PREPARATION", manifest["release_qualification"])


if __name__ == "__main__":
    unittest.main()

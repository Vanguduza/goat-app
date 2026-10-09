#!/usr/bin/env python3
"""Build and identify a frozen, owner-authorized Android test APK. Never qualifies a release."""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = "Vanguduza/goat-app"
PACKAGE = "com.farmos.app"
TARGET = ":app:assembleDebug"
BLOCKED_INPUTS = (
    "FARM_OS_SUPABASE_URL", "FARM_OS_SUPABASE_PUBLISHABLE_KEY",
    "FARM_OS_E2E_EMAIL", "FARM_OS_E2E_PASSWORD", "FARM_OS_E2E_FARM_ID",
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def run(*args: str, env: dict[str, str] | None = None) -> str:
    result = subprocess.run(args, cwd=ROOT, env=env, capture_output=True, text=True, check=True)
    return result.stdout.strip()


def sha256(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def fingerprint(value: str, length: int) -> str:
    normalized = value.replace(":", "").strip().upper()
    require(bool(re.fullmatch(rf"[0-9A-F]{{{length}}}", normalized)), "Invalid certificate fingerprint")
    return normalized


def display_fingerprint(value: str) -> str:
    return ":".join(value[index:index + 2] for index in range(0, len(value), 2))


def source_identity(expected: str) -> dict:
    require(bool(re.fullmatch(r"[0-9a-f]{40}", expected)), "Supply the full expected commit SHA")
    require(run("git", "rev-parse", "--show-toplevel") == str(ROOT), "Unexpected repository root")
    remote = run("git", "remote", "get-url", "origin")
    require(remote in (
        f"https://github.com/{REPOSITORY}.git", f"https://github.com/{REPOSITORY}",
        f"git@github.com:{REPOSITORY}.git",
    ), "Origin must identify Vanguduza/goat-app without embedded credentials")
    head = run("git", "rev-parse", "HEAD")
    require(head == expected, "HEAD changed or differs from the expected candidate")
    require(not run("git", "status", "--porcelain=v1", "--untracked-files=all"),
            "Commit or isolate all source changes before preparing a test artifact")
    ref = run("git", "symbolic-ref", "--quiet", "--short", "HEAD")
    return {"repository": REPOSITORY, "commit": head, "tree": run("git", "rev-parse", "HEAD^{tree}"), "ref": ref}


def reject_provider_inputs() -> None:
    # Command-line empty properties below also override user-level Gradle properties.
    # Reject accidental live-server/test credentials without displaying their values.
    require(not any(os.environ.get(name, "").strip() for name in BLOCKED_INPUTS),
            "Remove server-era FARM_OS_SUPABASE/E2E environment inputs for this local-first test build")
    for path in (ROOT / "gradle.properties", Path.home() / ".gradle/gradle.properties"):
        if not path.is_file():
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            stripped = line.strip()
            if not stripped or stripped.startswith(("#", "!")):
                continue
            for name in BLOCKED_INPUTS:
                match = re.match(rf"^{re.escape(name)}(?:\s*[:=]\s*|\s+)(.*)$", stripped)
                require(not match or not match.group(1).strip(),
                        "Remove server-era FARM_OS_SUPABASE/E2E Gradle inputs for this test build")
    require(not os.environ.get("ANDROID_USER_HOME") and not os.environ.get("ANDROID_SDK_HOME"),
            "Use the standard Android user directory so the retained test keystore is unambiguous")


def debug_certificate(keytool: Path, expected_sha1: str) -> bytes:
    keystore = Path.home() / ".android/debug.keystore"
    require(keystore.is_file() and not keystore.is_symlink(),
            "Retain the existing external debug keystore first; this script never creates or rotates a key")
    require(keystore.stat().st_uid == os.getuid() and stat.S_IMODE(keystore.stat().st_mode) & 0o077 == 0,
            "The retained test keystore must be owned by the build user and readable only by that user")
    # Android's conventional debug password is not a production secret. Only the public certificate is read.
    result = subprocess.run(
        [str(keytool), "-exportcert", "-alias", "androiddebugkey",
         "-keystore", str(keystore), "-storepass", "android"],
        capture_output=True, check=True,
    )
    cert = result.stdout
    require(hashlib.sha1(cert).hexdigest().upper() == expected_sha1,
            "The retained keystore does not match the pinned Google test certificate")
    return cert


def apk_identity(signature_output: str, badging: str, expected_sha1: str) -> tuple[dict, str]:
    require(re.findall(r"^Number of signers: ([0-9]+)$", signature_output, re.M) == ["1"],
            "Require exactly one reported APK signer")
    # SDK 37 labels the verified signer by scheme (for example, "V2 Signer:").
    # Earlier SDK tools use "Signer #1". Both must describe one identical certificate.
    signer = r"(Signer #[1-9][0-9]*|V[1-4](?:\.[0-9]+)? Signer:)"
    sha1s = re.findall(rf"^{signer} certificate SHA-1 digest: ([0-9a-fA-F]+)$", signature_output, re.M)
    sha256s = re.findall(rf"^{signer} certificate SHA-256 digest: ([0-9a-fA-F]+)$", signature_output, re.M)
    require(len(sha1s) == 1 and len(sha256s) == 1, "Require exactly one verified APK signing certificate")
    require(sha1s[0][0] == sha256s[0][0], "APK certificate digest records refer to different signers")
    cert_sha1 = fingerprint(sha1s[0][1], 40)
    cert_sha256 = fingerprint(sha256s[0][1], 64)
    require(cert_sha1 == expected_sha1, "APK signer differs from the pinned Android OAuth certificate")
    certificates = re.findall(r"-----BEGIN CERTIFICATE-----\s+([A-Za-z0-9+/=\s]+?)-----END CERTIFICATE-----", signature_output)
    require(len(certificates) == 1, "Require one exported public APK certificate")
    der = base64.b64decode(re.sub(r"\s+", "", certificates[0]), validate=True)
    require(hashlib.sha1(der).hexdigest().upper() == cert_sha1 and
            hashlib.sha256(der).hexdigest().upper() == cert_sha256,
            "Exported public certificate does not match the verified APK signer")
    package_line = re.search(r"^package: (.*)$", badging, re.M)
    require(package_line is not None, "APK package metadata is missing")
    package = dict(re.findall(r"(\w+)='([^']*)'", package_line.group(1)))
    require(package.get("name") == PACKAGE, "APK package differs from the Android OAuth package")
    require(bool(re.fullmatch(r"[0-9]+", package.get("versionCode", ""))) and
            bool(package.get("versionName")), "APK version metadata is missing")
    # aapt2 37 names this minSdkVersion; older build tools print sdkVersion.
    minimum = re.findall(r"^(?:minSdkVersion|sdkVersion):'([0-9]+)'$", badging, re.M)
    target = re.findall(r"^targetSdkVersion:'([0-9]+)'$", badging, re.M)
    require(len(minimum) == 1 and len(target) == 1, "APK SDK metadata is missing or ambiguous")
    require(re.search(r"^application-debuggable(?:\s|$)", badging, re.M) is not None,
            "This preparation command is only for the existing debug testing variant")
    return {
        "package": package["name"], "version_code": int(package["versionCode"]),
        "version_name": package["versionName"], "minimum_sdk": int(minimum[0]),
        "target_sdk": int(target[0]), "debuggable": True,
        "certificate_sha1": display_fingerprint(cert_sha1),
        "certificate_sha256": display_fingerprint(cert_sha256),
    }, "-----BEGIN CERTIFICATE-----\n" + certificates[0].strip() + "\n-----END CERTIFICATE-----\n"


def evidence_revision(path: str) -> dict:
    return {"path": path, "git_blob": run("git", "rev-parse", f"HEAD:{path}"), "sha256": sha256(ROOT / path)}


def prepare(args: argparse.Namespace) -> Path:
    expected_sha1 = fingerprint(args.expected_sha1, 40)
    initial = source_identity(args.expected_commit)
    reject_provider_inputs()
    sdk = args.sdk.resolve()
    java = args.java_home.resolve()
    build_tools = sdk / "build-tools" / args.build_tools
    for path in (java / "bin/java", java / "bin/keytool", build_tools / "apksigner", build_tools / "aapt2"):
        require(path.is_file(), f"Required build tool is unavailable: {path}")
    cert = debug_certificate(java / "bin/keytool", expected_sha1)
    canonical = json.loads((ROOT / "PROJECT_CANONICAL_STATE.json").read_text(encoding="utf-8"))
    completion = json.loads((ROOT / "PROJECT_COMPLETION_STATE.json").read_text(encoding="utf-8"))
    output = args.output_dir.resolve()
    require(not output.is_relative_to(ROOT), "Keep test outputs outside the source checkout")
    require(not output.exists(), "Use a new output directory; existing evidence is never overwritten")
    output.mkdir(parents=True, mode=0o700)
    env = dict(os.environ, JAVA_HOME=str(java), ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk))
    command = [
        "./gradlew", TARGET, "--no-daemon", "--max-workers=1",
        "-Dorg.gradle.parallel=false", "-Pkotlin.compiler.execution.strategy=in-process",
        f"-Dorg.gradle.jvmargs=-Xmx{args.heap_mb}m -Dfile.encoding=UTF-8", "--console=plain",
    ] + [f"-P{name}=" for name in BLOCKED_INPUTS]
    # Force a new APK assembly without deleting unrelated caches, local records, or build reports.
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    if apk.exists():
        require(apk.is_file() and not apk.is_symlink(), "Unexpected APK output type")
        apk.unlink()
    started = datetime.now(timezone.utc).isoformat()
    with (output / "build.log").open("w", encoding="utf-8") as log:
        subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT,
                       timeout=args.timeout_seconds, check=True)
    require(source_identity(args.expected_commit) == initial, "Sources changed during APK preparation")
    require(debug_certificate(java / "bin/keytool", expected_sha1) == cert, "Test certificate changed during build")
    require(apk.is_file() and not apk.is_symlink() and apk.stat().st_size > 0,
            "Gradle did not produce the expected regular debug APK")
    # Retain the output before inspecting it. Another build may replace the shared Gradle APK path,
    # but the package, certificate and digest below always describe these same retained bytes.
    name = f"goat-test-{initial['commit'][:12]}.apk"
    retained = output / name
    shutil.copy2(apk, retained)
    require(retained.is_file() and not retained.is_symlink(), "Unexpected retained APK type")
    digest = sha256(retained)
    signature = run(str(build_tools / "apksigner"), "verify", "--verbose", "--print-certs", "--print-certs-pem", str(retained), env=env)
    badging = run(str(build_tools / "aapt2"), "dump", "badging", str(retained), env=env)
    identity, pem = apk_identity(signature, badging, expected_sha1)
    require(sha256(retained) == digest, "The retained APK changed during identity verification")
    (output / "apk-signature.txt").write_text(signature + "\n", encoding="utf-8")
    (output / "apk-manifest.txt").write_text(badging + "\n", encoding="utf-8")
    (output / "test-signing-certificate.pem").write_text(pem, encoding="ascii")
    require(source_identity(args.expected_commit) == initial, "Sources changed before provenance was saved")
    java_version = subprocess.run([str(java / "bin/java"), "-version"], capture_output=True, text=True, check=True)
    manifest = {
        "schema_version": 1, "purpose": "OWNER_AUTHORIZED_TESTING_ONLY",
        "release_qualification": "NOT_GRANTED_BY_BUILD_PREPARATION",
        "source": initial,
        "build": {"target": TARGET, "variant": "debug", "command": command, "started_at_utc": started,
                  "finished_at_utc": datetime.now(timezone.utc).isoformat(),
                  "jdk": java_version.stderr.strip() or java_version.stdout.strip(),
                  "sdk_build_tools": args.build_tools,
                  "gradle_wrapper_properties_sha256": sha256(ROOT / "gradle/wrapper/gradle-wrapper.properties"),
                  "provider_inputs": "SERVER_ERA_SUPABASE_AND_E2E_VALUES_FORCED_EMPTY"},
        "application": identity,
        "artifact": {"filename": name, "sha256": digest, "size_bytes": (output / name).stat().st_size},
        "canonical_state": dict(evidence_revision("PROJECT_CANONICAL_STATE.json"),
                                release_blocked=canonical["canonical_state"]["release_blocked"]),
        "completion_state": dict(evidence_revision("PROJECT_COMPLETION_STATE.json"),
                                 programme_status=completion["programme_status"], coverage=completion["coverage"]),
        "acceptance": {
            "build_and_apk_identity": "VERIFIED_BY_THIS_COMMAND",
            "candidate_ci": "RECORD_SEPARATELY_AGAINST_EXACT_SOURCE",
            "google_cloud_registration": "NOT_VERIFIED_BY_THIS_COMMAND",
            "live_drive_and_device_tests": "NOT_EXECUTED_BY_THIS_COMMAND",
            "visual_and_product_qualification": "NOT_GRANTED",
        },
    }
    record = output / "test-build-provenance.json"
    record.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return record


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-commit", required=True)
    parser.add_argument("--expected-sha1", required=True, help="Public certificate SHA-1 registered for this testing identity")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--build-tools", default="37.0.0")
    parser.add_argument("--heap-mb", type=int, default=1536, choices=(768, 1024, 1536, 2048))
    parser.add_argument("--timeout-seconds", type=int, default=1800, choices=range(60, 3601))
    args = parser.parse_args()
    try:
        record = prepare(args)
    except (ValueError, OSError, subprocess.SubprocessError) as failure:
        # Do not print captured credential-bearing subprocess output.
        print(f"Test build preparation stopped: {type(failure).__name__}: {failure}", file=sys.stderr)
        raise SystemExit(1) from None
    print(f"Verified testing artifact and public identity recorded in {record}; release qualification is unchanged.")


if __name__ == "__main__":
    main()

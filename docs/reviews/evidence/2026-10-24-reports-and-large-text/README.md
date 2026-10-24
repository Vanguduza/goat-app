# Native visual corrections — 24 October 2026

These review candidates preserve actual rendered screenshot bytes. They are not replacements for locked reference images or an owner approval.

Before source: e085e95270d3b0779cae015f204d3a3494136783 (all27 CI configurations passed, but these pixel defects still existed).
After exact Kotlin/Gradle source: 38f975630a576eec0093dde83cde630382472fbb. The local runs began with HEAD e085e95 and modified source; all retained source fingerprints exactly match the later committed code. Both selected runs passed45 tests with zero failures/errors/skips and no edits during execution.

Open index.html for the before/after page. manifest.json binds every image and observation to its SHA-256 and actual test case. Per-case native metadata remains unmodified under metadata/.

The existing fullscope audit has298 observed owners across all27 configurations of545 registered IDs. These counts are source-specific, not a full visual certification. New changes require their own full CI rerun. Independent owner review, missing contracts, state/role coverage and physical device/live Drive qualification are still separate requirements.

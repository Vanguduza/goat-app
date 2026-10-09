# Additional runtime-navigation case evidence

This inventory adds reviewed JVM test cases to Phase 5 without changing the historical
59-class source groups or the 156-feature, 29-module, 545-screen product scope. It is an
evidence-consistency check, not an approval authority or a completion engine.

## What a mapped case means

[runtime-navigation-cases.json](runtime-navigation-cases.json) identifies each additional test
source by its exact SHA-256, source commit, package/class and complete set of JUnit methods.
A source change requires explicit review of the mapping; a new method cannot silently inherit a
class-level screen claim.

Each case contains either reviewed positive assertions or an execution-only reason.

- A rendered surface-owner claim has a positive registered screen/atom assertion.
- A rendered destination traversal also identifies the actual return action and a subsequent positive
  restoration assertion. A disappearing old screen alone does not establish the destination.
- Security, current-session authority, retained-callback, SQL-only and controlled-provider cases
  remain execution evidence without additional traversal claims. Their deliberately absent tags
  do not add screen coverage.

The source checker supports the specific reviewed direct assertions and helper paths used here.
It resolves the explicit Sheep/Cattle helper binding instead of treating an interpolated ID as every
species. It refuses unsupported helpers, negative assertions, comment/string-only anchors, changed
selector bindings and a return that precedes entry. This is not a general Kotlin control-flow proof:
the source hash and reviewed mapping remain necessary, alongside actual successful test execution.

The legacy 59-source inventory retains its existing classification and historical provenance. Its
whole-file lexical screen extraction is not retroactively presented as per-case attribution. Additional
sources cannot overwrite any legacy source group.

## Before freezing a new mapping

Finish and commit the affected test sources first. Review each actual test method and its positive
assertions, then update its exact source hash and source checkpoint in the map. In a checkout containing
those commits, verify that every checkpoint actually contains the mapped bytes:

~~~sh
python3 - <<'PY'
from pathlib import Path
import sys
sys.path.insert(0, "scripts/design")
from runtime_navigation_cases import load_case_map, verify_reviewed_source_commits
root = Path.cwd()
verify_reviewed_source_commits(load_case_map(root), root)
PY
~~~

A shallow CI checkout need not contain the historical review commits. It must contain the exact
hash-matching test sources and map in the candidate tree. The freeze check above is separate from
Owner approval; a source checkpoint is not an approval receipt.

## Pending inventories

The normal source-evidence refresh remains CI_PENDING. It records expected source/case/claim
identities but sets additional executed cases and all runtime execution counts to zero. It accepts no
run ID or execution artifact as pending evidence.

~~~sh
python3 scripts/design/build-runtime-navigation-evidence.py --self-test
~~~

This command also runs the focused parser/rejection tests. It neither starts Gradle nor certifies a
provider, physical device, screen design, feature, module or MVP. The standard architecture guard
already invokes this builder self-test, so the new consistency tests run on that existing gate.

## Admitting a bounded passing runtime inventory

First independently verify the first-party Foundation run, its terminal checks, exact candidate and
retained artifact/log provenance. A typed run ID or repository JSON is not authentic authority.
Use the goat-native-test-evidence artifact from that run's native Android job, including these unchanged members:

- app/build/test-evidence/commit.txt
- app/build/test-evidence/tree.txt
- app/build/test-evidence/worktree-status.txt
- app/build/test-results/testDebugUnitTest/TEST-<fully-qualified-class>.xml for every mapped source

Retain the original timestamped native-job log as well. The checker requires the candidate tree to
match the actual tested checkout tree, a clean retained checkout, committed source/map bytes,
one actually executed app:testDebugUnitTest task, and current-run JUnit suite timestamps. FROM-CACHE,
UP-TO-DATE, skipped, failed, incomplete or duplicate case results cannot become new execution.
An actual PR merge checkout with the same tree is recorded separately from the source candidate.

After the independent run verification, the existing passing builder gains two required inputs:

~~~sh
python3 scripts/design/build-runtime-navigation-evidence.py \
  --status PASS_EXACT_HEAD_CI \
  --run-id VERIFIED_FOUNDATION_RUN_ID \
  --case-evidence-root /path/to/retained/native-test-artifact \
  --case-test-log /path/to/retained/timestamped-native-job.log
~~~

Do this from a clean current checkout whose HEAD still identifies the actual tested source candidate.
The first passing builder writes derived evidence; run it before editing any other evidence files.
Dirty production, build or documentation inputs cannot borrow the clean CI checkout's proof.
The checker preserves raw
JUnit SHA-256 values, exact class/method identities, suite timestamps, actual checkout/tree and log
digest. A later evidence-only commit can retain that source proof; it cannot relabel a different source
tree as the tested candidate. The existing runtime ownership and gap consumers validate the same
case map and counts before using the additional claims.

Collectors must inventory both the legacy test_sources paths and additional_case_evidence.sources.
Some additional classes have only execution-only cases and intentionally appear in no screen proof
group. Omitting those classes would lose their security/session evidence; adding their negative
screen tags to a traversal group would misstate it.

## Boundaries this evidence does not cross

JUnit parser fixtures are synthetic tests of the evidence checker itself. The new Android
LocalFarmActivityJourneyTest and LocalAccessRecoveryDurabilityTest belong to the separate connected
instrumentation gate; JVM reports cannot certify their execution.

Controlled carrier handles, ContentProviders and ActivityResult registries do not establish live Google
delivery or physical-device behaviour. Source/Room/Compose test counts are not feature counts.
The builder leaves visual_green_screens, feature_green and module_green at zero and mvp_green false.
Owner visual approval, complete feature/module contracts and whole-product qualification still need
their own authentic evidence under Project Truth.

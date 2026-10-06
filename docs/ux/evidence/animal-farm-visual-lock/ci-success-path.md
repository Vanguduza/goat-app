# CI success path (from GitHub Actions payloads)

Fetched 2026-09-06T21:50Z. This is not PROJECT_GREEN. Local PASS is not GitHub-hosted certification. Do not skip or weaken goat-app workflows.

Failure classes: **A** hosted runner never assigned · **B** lint/format · **C** typecheck · **D** unit/DB fixture · **E** frontend compile/workbench · **F** Playwright webServer timeout · **G** Node engine warning (non-blocking) · **H** other.

---

## 0. Ordered gates (do these in this order)

1. **Owner, goat-app billing** — until this is done, every goat-app job stays class A. No source change can turn GitHub green.
2. **Owner, dde cherry-pick lint onto `claude/dde-069-frontend-studio-v2-yn110e`** — token cannot push (403). After this, dde `CI` / `Lint` can go green.
3. **dde typecheck** — still red after lint (73 mypy errors locally). Not a 5-line generated-contracts fix.
4. **dde unit tests** — GitHub has never reached this step on the claude branch. `main` last hosted result is class D.
5. **dde Studio compile + workbench** — independent of python lint; still red on the last Studio run.
6. **goat-app hosted jobs after billing** — smoke is the billing canary; then handover + foundation; `android-device-e2e` only after `android` is green.

---

## 1. Vanguduza/goat-app — all current red is class A

### Current failing run IDs (HEAD `6cbc4f47c11e384683bc33dbef925ff84c3051ca`, PR #3)

| Workflow | Run ID | Job | Job ID | runner_id | steps | Checkout |
|---|---|---|---|---|---|---|
| Actions smoke | [34061979570](https://github.com/Vanguduza/goat-app/actions/runs/34061979570) | smoke | 101564000923 | 0 | [] | never |
| Animal Farm handover integrity | [34061979586](https://github.com/Vanguduza/goat-app/actions/runs/34061979586) | handover-integrity | 101564000997 | 0 | [] | never |
| Farm OS foundation CI | [34061979644](https://github.com/Vanguduza/goat-app/actions/runs/34061979644) | android | 101564001349 | 0 | [] | never |
| same | same | edge-functions | 101564001409 | 0 | [] | never |
| same | same | meilisearch-contract | 101564001352 | 0 | [] | never |
| same | same | search-pipeline | 101564001254 | 0 | [] | never |
| same | same | supabase | 101564001366 | 0 | [] | never |
| same | same | android-device-e2e | 101564007044 | null | [] | skipped (`needs: android`) |

### Same class A on the other named branches

- `implementation/animal-farm-visual-lock` handover [34040255685](https://github.com/Vanguduza/goat-app/actions/runs/34040255685) job 101505591144 (`runner_id` 0, steps []).
- `agent/animal-farm-gate1-recovery` SHA `c603a903b570e5519cdc571b88a2c166177900ba`: smoke [34047633990](https://github.com/Vanguduza/goat-app/actions/runs/34047633990), handover [34047634009](https://github.com/Vanguduza/goat-app/actions/runs/34047634009), foundation [34047634013](https://github.com/Vanguduza/goat-app/actions/runs/34047634013). Same empty-step pattern.
- PR #1 `implementation/foundation-vertical-slice` latest smoke check-run 101248824163: same annotation.

### Exact annotation (every failed goat-app job that has annotations)

> The job was not started because recent account payments have failed or your spending limit needs to be increased. Please check the 'Billing & plans' section in your settings

Logs: `gh run view … --log-failed` → `log not found` (job never started).

Billing cutoff: last hosted **success** 2026-09-03T20:38:57Z runs [33803481947](https://github.com/Vanguduza/goat-app/actions/runs/33803481947) (foundation) and [33803481961](https://github.com/Vanguduza/goat-app/actions/runs/33803481961) (smoke) on `implementation/foundation-vertical-slice` SHA `7d01b6bb…` with real runners (`runner_id` 1000004649–1000004652, checkout succeeded). First class-A fail 2026-09-03T22:29:14Z run [33813272072](https://github.com/Vanguduza/goat-app/actions/runs/33813272072) (smoke job 100839744649, `runner_id` 0, same billing annotation).

### What must be true before any goat-app job can be green

A GitHub-hosted `ubuntu-latest` runner is assigned (`runner_id` nonzero, `runner_name` like `GitHub Actions 1000…`, step 1 `Set up job` exists). That is an **owner billing action**, not a source fix.

**Smallest owner action**

1. Org/user **Settings → Billing & plans**: clear failed payments and/or raise the Actions spending limit until hosted minutes are allowed.
2. Re-run the three PR #3 workflows (or push any commit). Do not edit `.github/workflows/*.yml` to skip jobs.

**Canary that billing is fixed:** Actions smoke run duration > ~2s, job `smoke` has steps `[Set up job, Runner available, Complete job]`, conclusion success. That job is `echo "GitHub Actions runner is available for Farm OS"` — if it is still red, billing is still blocking.

### Local equivalent (does not replace hosted)

```bash
# optional overrides; script defaults are:
#   JAVA_HOME=$HOME/.local/jdk/jdk-17.0.20.1+1
#   ANDROID_HOME=$HOME/.local/android-sdk
#   GRADLE_HOME=$HOME/.local/gradle/gradle-9.3.1
#   DENO_HOME=$HOME/.local/deno
#   MEILI_BIN=$HOME/.local/meilisearch/meilisearch-v1.53.1
#   MEILI_HOST=http://127.0.0.1:7700
#   MEILI_MASTER_KEY=farm-os-ci-master-key-32-bytes-minimum
#   MEILI_INDEX_PREFIX=farm-os
export JAVA_HOME ANDROID_HOME GRADLE_HOME DENO_HOME
export PATH="${JAVA_HOME}/bin:${GRADLE_HOME}/bin:${DENO_HOME}/bin:${PATH}"
# supabase + Docker required for search-pipeline and supabase jobs
bash scripts/ci/run-local.sh --out docs/ux/evidence/animal-farm-visual-lock/local-ci-report.json
```

Last local report (`tested_commit` `0824aa9`, evidence class `LOCAL_CI_ALTERNATIVE_NOT_GITHUB_HOSTED_CERTIFICATION`): PASS smoke, handover-integrity, android, edge-functions, meilisearch-contract, local-nav-audit. UNAVAILABLE android-device-e2e, search-pipeline, supabase (missing emulator / supabase CLI / Docker on that host). FAIL count 0.

### Job-by-job after billing is fixed (what will still be red)

| Job | After billing | Still red if |
|---|---|---|
| smoke | should go green immediately | billing still blocked |
| handover-integrity | likely green (local PASS; needs checkout + node 22) | pack/registry scripts fail on hosted |
| android | likely green (local PASS including visual-authority + gradle tests) | hosted JDK/SDK/gradle differ |
| edge-functions | likely green (local PASS; deno 2.9.6) | deno check/test fail |
| meilisearch-contract | likely green (local PASS; docker `getmeili/meilisearch:v1.53.1`) | docker/port 7700 |
| search-pipeline | **unproven on this branch** (local UNAVAILABLE) | `verify-search-pipeline.sh` / supabase CLI 2.116.0 |
| supabase | **unproven on this branch** (local UNAVAILABLE) | `supabase start` / `db reset` / `test db` |
| android-device-e2e | skipped until android greens; then **unproven on this branch** (local UNAVAILABLE). Last hosted success was on the older foundation SHA, ~14 min emulator job | KVM/AVD/`run-two-device-e2e.sh` |

---

## 2. Vanguduza/dde — runners are assigned; source gates are serial

Push to dde from this token: `Permission to Vanguduza/dde.git denied to cursor[bot]` (HTTP 403). Local tree `/tmp/dde` branch `cursor/wrap-live-e2e-e501-b11a`.

### 2.1 Branch `claude/dde-069-frontend-studio-v2-yn110e` (authoritative DDE-069 line)

#### CI run [34053784705](https://github.com/Vanguduza/dde/actions/runs/34053784705) SHA `ebee865ccb04446b10d8cc4848911637b1de763f`

| Job | Job ID | runner | Checkout | Failed step | Class | First real error |
|---|---|---|---|---|---|---|
| ci | 101541892827 | GitHub Actions 1000005462 ubuntu-latest | yes | Lint (`just lint`) | **B** | `E501 Line too long (94 > 88)` `tests/live/test_claude_design_live_e2e.py:351:89` `Found 1 error.` `recipe lint failed on line 33` |
| windows | 101541892955 | GitHub Actions 1000005461 windows-latest | yes | Lint | **B** | same E501 (Windows path `tests\live\test_claude_design_live_e2e.py:351`) |

Typecheck / contract / unit / migration / integration **did not run** (lint is earlier in `.github/workflows/ci.yml`).

Class **G** on the same jobs (non-blocking annotation): Node.js 20 deprecated, actions forced onto Node 24.

#### DDE Studio run [34051500250](https://github.com/Vanguduza/dde/actions/runs/34051500250) SHA `843c61e8ceb88aef0ccc126353d62f3d91f4daf4`

(Studio did not re-run on `ebee865` — path filter; last Studio is this SHA.)

| Job | Job ID | runner | Checkout | Failed step | Class | First real error |
|---|---|---|---|---|---|---|
| design-gates | 101535834780 | 1000005324 | yes | (none) | green | — |
| compile | 101535834953 | 1000005327 windows-latest | yes | Client tests (PR-blocking) | **E** | `npm run check` and `tsc` succeeded; then `Could not find 'D:\a\dde\dde\interfaces\dde-studio\out\shared\*.test.js'` (`package.json` `"test": "… node --test out/shared/*.test.js"` — PowerShell does not expand the glob). Tests exist as `interfaces/dde-studio/shared/*.test.ts`. |
| workbench | 101535834912 | 1000005326 | yes | Structural conformance at 1672x941 | **F** | Typecheck + Build succeeded; `npm run test:visual` → `Error: Timed out waiting 120000ms from config.webServer.` (`npx vite --port 4319 --strictPort` never served `http://127.0.0.1:4319/visual/fixture.html`). Artifact upload found no files. |
| visual | 101535921513 | null | no | skipped | skipped | `needs: compile` |

### Exact lint success path (class B) — already in the local tree

On `/tmp/dde` `cursor/wrap-live-e2e-e501-b11a`:

- `f9ded3fb6dfc2f19a487b7c2a23e9f4b7dabc0b8` wraps the 94-char assertion (the exact GitHub E501).
- `adb17a0fdb530b545c8fbb937cfccdac841abda8` applies `ruff format` so `just lint`'s second line (`ruff format --check .`) also passes.

Local proof: `just lint` → `All checks passed!` / `1000 files already formatted`, exit 0.

**Smallest owner action (required; agents cannot push):**

```bash
git fetch origin
git checkout claude/dde-069-frontend-studio-v2-yn110e
git cherry-pick f9ded3fb6dfc2f19a487b7c2a23e9f4b7dabc0b8 adb17a0fdb530b545c8fbb937cfccdac841abda8
just lint          # must be exit 0 before push
git push origin claude/dde-069-frontend-studio-v2-yn110e
```

If those SHAs are only in this VM: copy the two diffs (4 files: `tests/live/test_claude_design_live_e2e.py`, `engine/gateway/api.py`, `engine/studio/mutations/executor.py`, `tests/unit/test_claude_design_transport.py`) onto the branch and push.

**What stays red after that push:** CI job proceeds to **Typecheck** (`just typecheck` / `uv run mypy`).

### Next gate after lint — class C (do not treat lint as CI-green)

Local mypy on `ebee865` and on the wrap branch (lint-only delta):

```
Found 73 errors in 5 files (checked 550 source files)
```

`main` at `b89bc6c8c1663449b467edd8ae13bec4ba98e642`: `Success: no issues found in 398 source files`.

71/73 errors are `ExperienceRecord` kwargs/attrs (`experience_origin`, `eligible_for_routing_training`, …) in `engine/learning/{service,repository,activation,activation_service}.py`. Generated `engine/contracts/experience_record.py` and `schemas/objects/experience_record.json` **do not define those fields** (`additionalProperties: false`). Regenerating contracts from the current schema would not add them. Remaining 2: `engine/gateway/commands.py:1293` (`_studio` / `Any` return). This is not a 5-line generated-contracts root cause; do not ship a fake typecheck patch.

**Smallest code action for C:** schema + generated contract + learning service alignment (or stop constructing fields the contract forbids). That is a real DDE-069 contract change, not a wrap.

**What stays red after typecheck is eventually green:** GitHub has never executed `just test` on this branch. See class D below.

### Class D — unit / DB fixture (proven on `main` and older PRs; not yet reached on claude)

`main` CI [33884779430](https://github.com/Vanguduza/dde/actions/runs/33884779430) job `ci` 101061777700: Lint **passed**, Typecheck **passed**, then **Unit tests** failed:

```
asyncpg.exceptions.UndefinedTableError: relation "organizations" does not exist
============ 421 failed, 809 passed, 7 skipped in 355.99s ============
```

Workflow order is Lint → Typecheck → contracts → design-lints → **Unit tests (`just test`)** → then Alembic. Postgres service is empty when pytest runs. Windows job on `main` **succeeded** because it runs `just test-unit` (`-m "not integration"`).

Same Unit tests step failed on:

- PR #2 `docs/rev3-consolidated-canonical` CI [33684142129](https://github.com/Vanguduza/dde/actions/runs/33684142129)
- PR #1 `rev3-implementation` CI [33591978176](https://github.com/Vanguduza/dde/actions/runs/33591978176)

`0f9a236` (“close PostgreSQL and Redis integration blockers”) is local-host evidence (`docs/evidence/dde-069/POSTGRES_REDIS_CLOSURE.md`). It is **not** a GitHub Unit tests pass on claude; that step has not run there.

### Class E — compile client tests (Windows glob)

Proven command that matches the log:

```bash
cd interfaces/dde-studio
npm ci
npm run check          # already green on the failing job
npm run compile        # already green
node --test out/shared/*.test.js   # Windows: looks for a literal *.test.js
```

Smallest code action: make `scripts.test` Windows-safe (e.g. `node --test out/shared/` without a shell glob). Not done in the wrap branch (python-only).

**What stays red after a glob fix:** `visual` unblocks only if compile is green; workbench is a separate job and is class F.

### Class F — workbench Playwright webServer

```bash
cd interfaces/dde-studio/ui
npm ci
npm run check
npm run build
npx playwright install --with-deps chromium
npm run test:visual    # playwright config webServer: npx vite --port 4319 --strictPort
                       # url: http://127.0.0.1:4319/visual/fixture.html  timeout 120000
```

Fixture file exists in tree. GitHub never printed a vite error — only the 120s timeout. Diagnose from vite stdout before changing YAML.

### Class G — ignore for green/red

Node 20 deprecation warning on checkout/setup actions. Jobs fail for B/E/F, not for this warning.

---

## 3. Other dde PRs (still red; different heads)

### PR #3 `cursor/dde-069-frontend-studio-v2-b539` SHA `698bd36`

- CI [33923491169](https://github.com/Vanguduza/dde/actions/runs/33923491169) / push [33923488086](https://github.com/Vanguduza/dde/actions/runs/33923488086): Lint **B** `E501` Found **3** errors (92/90/89 > 88) — not the later single 94-char line. Wrap commits based on `ebee865` are **not** a drop-in for this head.
- Studio [33923491157](https://github.com/Vanguduza/dde/actions/runs/33923491157): compile **E** (same glob), workbench **F** (same 120s), design-gates green, visual skipped.

Owner should land lint on `claude/dde-069-frontend-studio-v2-yn110e`, not this stale WIP, unless they rebase it.

### PR #2 / PR #1

Class **D** Unit tests (see 2.1). Windows jobs succeeded. Studio on PR #1 succeeded (older tree).

---

## 4. What nobody should be surprised by

| After this action | goat-app | dde CI (`ci`+`windows`) | dde Studio |
|---|---|---|---|
| Owner pays / raises Actions limit | smoke can go green; other jobs **start**. Not PROJECT_GREEN. search-pipeline, supabase, android-device-e2e still unproven on this branch. | unchanged | unchanged |
| Owner cherry-picks f9ded3f+adb17a0 to claude | unchanged | Lint green; **Typecheck red (73 mypy)** | unchanged unless studio paths change (Studio may not even re-run) |
| Someone later fixes typecheck for real | unchanged | Typecheck green; **Unit tests likely still red** until empty-DB/`organizations` / migrate-before-pytest is solved (main: 421 failed) | compile **E** + workbench **F** still red |
| Windows glob fix in `interfaces/dde-studio/package.json` | unchanged | unchanged | compile can go green; **visual then runs**; workbench still **F** until vite/webServer is actually up |

No goat-app workflow was weakened. No dde push was attempted beyond the 403 proof.

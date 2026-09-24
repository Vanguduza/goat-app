# Farm OS — Project Truth Protocol

**Owner amendment:** 24 September 2026  
**Repository:** Vanguduza/goat-app

1. `main` is the sole persistent branch and canonical development authority. Temporary branches may exist only for an active pull request and are deleted after merge.
2. Before declaring implementation canonical, inspect `main`, every active pull request, accepted owner decisions, and source-of-truth documents. Silent feature thinning is forbidden.
3. Every merge must preserve all valid branch-only work or explicitly document why it is superseded. Green tests do not authorize dropping behavior, data, contracts, evidence, or owner locks.
4. The former orphan `project-truth-ledger` branch was frozen into `docs/project-state/project-truth-ledger-snapshot/` at source commit `4d2e548aabc2551cd90134759abebe1c740d9243` and then retired. It is historical evidence, not a second source of truth.
5. Current project-truth evidence is Git history + pull-request review + required GitHub Actions checks. `.github/workflows/project-truth-autolog.yml` retains the required `project-truth` check but no longer writes to another branch.
6. Releases remain blocked while `canonical_state.release_blocked` is true.
7. A release/build provenance record must identify repository, exact commit SHA, branch/ref, target/module, canonical-state revision, and applicable green-state evidence.
8. If remembered state conflicts with Git, reconcile against `main` and accepted owner decisions. Git evidence wins over memory; owner decisions govern intentional supersession.
9. Visual work has exactly one presentation authority: `docs/ux/animal-farm-visual-lock/`. Deleted superseded visual authorities must never be recreated as active guidance.

# Retired project-truth ledger snapshot

This directory preserves the final machine-written evidence from the former orphan `project-truth-ledger` branch immediately after PR #18 merged into `main`.

- Frozen source commit: `4d2e548aabc2551cd90134759abebe1c740d9243`
- Frozen on: 24 September 2026
- `CHANGE_LEDGER.jsonl`: historical append-only records captured by the retired workflow.
- `CURRENT_STATE.json`: final branch snapshot before retirement.

The auxiliary branch is intentionally retired. Current truth is the `main` Git history, active PR evidence and required GitHub Actions checks. Do not recreate a permanent ledger branch.

# Project truth ledger

Machine-written evidence branch. Do not merge into `main` and do not edit by hand.
It carries an orphan history so it never enters the product lineage.

- `CHANGE_LEDGER.jsonl` - append-only record of every commit pushed to `main`.
- `CURRENT_STATE.json` - latest observed repository state.

Written by `.github/workflows/project-truth-autolog.yml` on `main`.

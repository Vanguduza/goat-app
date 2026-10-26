# Installed-device feedback — 26 October 2026

The owner supersedes only the Home text in the historical approved HTML: use the current farm name and Command center, not Farm pulse or a header date. The immutable HTML, artwork, fonts and protected reference files remain unchanged.

## Completed implementation checkpoint

A single `FarmAppViewport` reserves actual safe-drawing/status/cutout/navigation insets around every route, including onboarding. Native child pages consume only remaining insets. Header/Work UI tests retain their original identity and navigation assertions while following the new popup selectors and focused task screen.

The generic weight action chooses one of five species, then an exact farm-local animal. Goat, rabbit and other-species herd commands remain the persistence/journal authority. Weight conversion is exact to grams; rabbit history retains its existing kilogram ledger. A same-day observation uses the original command timestamp, not a later clock read that could produce a future measurement. Poultry captures explicitly represent one tagged bird, not a flock average.

Task creation is a dedicated screen with repeat and assignee modal fields, an optional whole-farm animal search, and collapsed advanced task codes. Both one-off and recurring commands retain the exact animal ID. Current-farm and species validation occurs inside the existing atomic task/health command boundary; schema and protocol payloads are unchanged.

Health and care is embedded in goat adults/kids, rabbit profiles, sheep/lambs, cattle and poultry flocks. Per-subject queries include older records beyond recent-list limits, both rabbit weight ledgers, rabbit GI concerns, projected recurring follow-ups and native flock vaccination/biosecurity data. Missing history remains unknown. Evidence prompts do not diagnose or prescribe and reveal their supporting facts on demand. Existing handlers record confirmed observations/follow-ups. Child actions preserve the exact subject when returning to its profile.

## Validation at this checkpoint

`owner-feedback-closeout-r5-20261026` passed 36 checks with no failures, errors, skips, compile errors or source drift. It covers the five initially reproduced UI failures, five neutral health-rule checks, five exact-subject command tests including every weighing species, the inset contract, five Home interaction contracts, seven menu/task contracts and eight task-view/edit contracts. This is not a claim of full-app or physical-device approval. Broad regression and final source-bound CI remain separate required evidence.

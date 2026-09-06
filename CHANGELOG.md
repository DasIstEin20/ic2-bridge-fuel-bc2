# Changelog

## 1.0.3 — 2026-09-06

- Fixed stored FE reverting after world reloads when powered through Universal Cables: actual transfers mark the machine's loaded chunk for saving (including Forestry), and notify Refined Storage's separate network SavedData through its optional public API.
- Kept FE support capability-based; no separate Forestry adapter, machine lists, new conversion ratios or changes to cable distribution.
- Fixed corrupted arrows, status symbols and translations in Windows builds by packaging JSON resources explicitly as UTF-8.
- Updated the config title, aligned labels with their inputs, made input columns responsive and kept the Energy page clear of the footer.
- Save confirmations are green; unsaved field values survive window resizing.
- Added FE persistence/NBT, independent RS SavedData and no-op-transfer regression tests, plus an EN/PL resource-encoding release audit. Test code is not included in the mod JAR.
- Removed the GitHub Actions release workflow; release artifacts are built locally.

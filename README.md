# Auto-Crate — Minecraft 26.3

**Fabric client-side mod** for automating ExcellentCrates reward creation and saved box presets. No server plugins are required.

## Download

[GitHub Releases](https://github.com/itonkungx-x/Auto-Crate/releases) — choose the newest `crate-auto-reward-26.3-*.jar`, not the .sha256 checksum file.

## Installation (one time for auto-updates)

1. Install Fabric Loader + Fabric API for Minecraft 26.3.
2. Remove the previous `crate-auto-reward-*.jar` from `%APPDATA%\\.minecraft\\mods`. 
3. Put the newest `crate-auto-reward-26.3-*.jar` into `mods`.
4. Start Minecraft, open **F7 → อัปเดตม็อด**. The automatic update toggle is enabled by default and can be disabled.
5. Future releases are checked on startup, downloaded over HTTPS, validated with SHA-256 and staged in the game's config directory. **Close Minecraft normally** to install. A separate Java process waits for the old process to exit and replaces its JAR without requiring manual file removal. A `.backup` of the previous JAR is retained.
6. Reopen Minecraft. Read the installed version on the updater screen.

Updates never overwrite `config/crateautoreward` presets/rate rules, and MMO/SMP data is stored separately.

## Important: Private repository restriction

The GitHub repository is currently **Private**. The client deliberately contains **no access tokens**; unauthenticated users (including your Minecraft client) will see GitHub HTTP 404 when checking a private release.

**Owner action required:** On GitHub open `Settings → General → Danger Zone → Change repository visibility → Public`. Make sure you do not store secrets/private files in this repository before changing its visibility. Releases then become accessible without login.

If you prefer keeping the source repository private, a separate public release-distribution repository or authenticated download mechanism is required and is not included here.

## Build and publishing

GitHub Actions **Build and Release Auto-Crate** builds Fabric with Java 25 and Gradle 9.7.1 on each relevant push to `main`, runs a standalone updater smoke test, computes SHA-256 and publishes assets under a GitHub Release matching `mod_version` in `crate-auto-reward/gradle.properties`. **Increase `mod_version` for each actual new version** (e.g. 0.6.1) so clients detect it. The repository's default GitHub Actions token needs `contents:write` permission, granted by the workflow.

The first published release is [v0.6.0](https://github.com/itonkungx-x/Auto-Crate/releases/tag/v0.6.0).

## Controls

- **F6** start/stop automation
- **F7** rules / choose MMO or SMP / updater screen
- **F8** emergency stop
- **K** locally saved crate presets

All hotkeys are remappable under Minecraft **Options → Controls → Key Binds**.

## Diagnostics

For update status, inspect **F7 → อัปเดตม็อด**. Installation logs are written to `config/crateautoreward/updates/updater-result.log`.

If a downloaded version fails, the original `*.jar.backup` can be restored manually while Minecraft is closed. If your launcher uses a custom game directory, place the mod JAR directly inside that directory's `mods` folder.

The Fabric GUI automation itself still requires testing on your server version of ExcellentCrates. Test with expendable items first.

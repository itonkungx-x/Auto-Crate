## Auto-Crate v0.6.0 — Fabric 26.3

- Adds client-only GitHub Releases auto-updater with SHA-256 checks.
- Shows update status and controls inside the Thai settings UI.
- Supports automatic checks/downloads. The verified mod is replaced only after Minecraft exits, using a detached Java updater helper.
- Automatically retains the previous JAR as a `.backup` file; MMO/SMP profiles and saved box presets are not modified.
- No server plugin required.

**First-time setup:** Replace the old v0.5.1 JAR with v0.6.0 once. Future releases will update without manual file replacement.

**GitHub visibility:** To check and download releases without access tokens, the repository must be Public. This release is initially private until its owner changes visibility. The mod deliberately does not contain a GitHub token.

After downloading, restart Minecraft to activate the updated code. Updates are validated using the release's `.sha256` asset and `fabric.mod.json` metadata.

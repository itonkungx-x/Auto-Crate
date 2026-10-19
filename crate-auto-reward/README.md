# Crate Auto Reward v0.6.0 (Fabric / Minecraft 26.3)

This is the Fabric client-side component only. **No server plugin is required.**

## Auto Updater

Press **F7 → อัปเดตม็อด** to check GitHub Releases, download a new version, or toggle automatic updates.

The updater uses `https://api.github.com/repos/itonkungx-x/Auto-Crate/releases/latest`, requires a public release (no token is bundled), and validates the downloaded JAR against the official .sha256 file plus Fabric metadata. On normal Minecraft shutdown, an independent Java process waits for the game to close, backs up the current JAR and installs the staged update. Reopen the game to activate it. See the [root README](../../README.md) for setup and recovery instructions.

## Core functions

- F6: add unique inventory items via Anvil → Item Reward, then apply server GUI Rarity/Weight rules where supported
- F7: Thai rule editor, MMO/SMP profile selector and update settings
- F8: emergency stop
- K: locally saved box presets

All shortcuts can be changed in Minecraft's Key Binds menu.

## MMO / SMP Separation

Configurations are isolated by both the server address and the selected MMO/SMP profile. The separate local presets and their captured rules are stored at `config/crateautoreward/profiles/<hash>` and are **never removed by updating the JAR**.

Note: if MMO and SMP are two backends behind the same public proxy address, select the correct profile manually. Client-only mods cannot reliably identify the backend.

Saved presets record the item data and rules, but client-only code cannot generate real server-accepted custom items when they aren't in the player's inventory. Replay checks for matching inventory items and stops if any are missing. Always test the ExcellentCrates GUI flow with expendable items before bulk changes.

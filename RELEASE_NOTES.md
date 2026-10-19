## Auto-Crate v0.6.2 — Rarity and Weight dialog controls

- Fixes automatic Rarity/Weight configuration after Item Reward is successfully created.
- Finds Minecraft 26.3 dialog controls recursively, including nested TextInput and SingleOption widgets.
- Sets Weight to the configured number, cycles the Rarity button until it displays the desired level, then presses ✔ OK directly.
- Adds detailed diagnostic information if a server GUI layout differs, rather than guessing or clicking other rewards.
- Keeps the client-only mode, server MMO/SMP isolation, Presets, Thai UI, remappable keys and GitHub release updater.

First test one expendable reward and confirm both Rarity and Weight in ExcellentCrates. This version is compiled in CI but cannot be fully integration-tested against your live server in CI.

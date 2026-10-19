## Auto-Crate v0.6.5 — Correct ExcellentCrates Dialog + hotkey handling

**Fixed**
- ExcellentCrates `RewardWeightDialog` uses **`MultiActionDialog`**, not `SimpleDialog`; the v0.6.4 type check prevented Rarity/Weight from being submitted.
- The mod now reads `MultiActionDialog.common().inputs()` for the exact server-provided `rarity` single-option input and `weight` text input, binds them through one `DialogControlSet`, and submits the **OK** action.
- Item Reward continues to be selected from the server's `MultiActionDialog.actions()`.
- Fixed double F6/F8 handling between GUI keyboard events and `KeyMapping.consumeClick()`, which could start and immediately stop an automation.
- Added diagnostic version and state in chat if the action stops.

**Unchanged**
- Minecraft 26.3 Fabric-only mod (no server plugin)
- MMO/SMP local profile separation, saved Presets, Thai settings UI, remappable keys and GitHub auto-updater.

**Validation**: Code compiles and the detached JAR replacement helper is tested in CI; the live ExcellentCrates GUI workflow still requires a test on the user's server. Try an expendable reward and verify that the saved Rarity/Weight are correct.

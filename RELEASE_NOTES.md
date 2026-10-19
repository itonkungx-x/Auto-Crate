## Auto-Crate v0.6.3 — Item Reward invisible action button fix

- Fixes **MultiButtonDialogScreen** where only Back and Warning appear in `Screen.children()`.
- Reads the actual `MultiActionDialog.actions()` supplied by the server and finds the **Item Reward** action by label.
- Invokes Minecraft's built-in `DialogControlSet.createActionButton` callback to submit the reward, without screen-coordinate guesses.
- Waits for Crate Rewards to reopen. Submits the action only once to avoid accidentally creating duplicate rewards.
- Preserves Thai UI, saved MMO/SMP profiles, rarity/weight rules, presets, and GitHub Releases auto-updater.

**Live-server status:** Built and smoke-tested in CI, not yet validated with a real server dialog. Test one expendable item. If an action fails, send the in-game diagnostic message.

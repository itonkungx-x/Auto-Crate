## Auto-Crate v0.6.4 — Fix Rarity & Weight after Item Reward

- Corrects the "Weight 0 fields" error on ExcellentCrates Weight & Rarity dialogs.
- Builds a paired `DialogControlSet` using server-provided `CommonDialogData.inputs()` (TextInput Weight and SingleOption Rarity), instead of searching UI children.
- Sets Weight and selects the configured rarity, then submits both bound input values via the server's OK ActionButton.
- Validates required input types, rarity availability and prevents unrelated dialogs from being submitted.
- Keeps client-side Fabric-only operation, MMO/SMP profiles, crate presets, Thai UI and automatic GitHub updater.

**Testing:** This is a build-time validation, not a guaranteed live server integration test. Test one inexpensive reward and confirm the server saved both fields.

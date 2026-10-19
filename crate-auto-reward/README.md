# Crate Auto Reward v0.5.0 (Minecraft 26.3 / Fabric)

## No server plugin needed
Install only the Fabric mod locally. Server-side ExcellentCrates remains unchanged.

## Separate MMO / SMP profiles
Press **F7**, select MMO or SMP at the top right. Data is isolated by **server address + chosen MMO/SMP**. This protects same-name presets and rate rules from leaking between worlds. If the server is a proxy and MMO/SMP share the same public IP, change the profile manually when switching backend; a client-only mod cannot always detect the underlying backend.

## Permanent local presets
Press **K** or **F7 > กล่องที่เซฟ**. Enter a name such as สีเหลือง, then choose **บันทึกจากกระเป๋า** to snapshot unique inventory items with complete Minecraft item components and stack counts. Select a saved preset from the list. Open Crate Rewards and press F6 to replay only this saved set, in the chosen server profile. If even one original item is missing or changed (including count), the mod refuses to start and reports the missing item. Choose **ใช้ไอเทมทั้งหมด (ไม่ใช้ Preset)** to return to the previous whole-inventory behavior.

Storage: `config/crateautoreward/servers.json` plus `config/crateautoreward/profiles/<hash>/rules.json` and `presets.json`. Each preset has a `presets.json.bak` backup after changes.

**Client-only limitation:** The game client cannot create server-authorized custom items from a snapshot. Presets remember what items to use, but the items must actually exist in the player's current inventory before replay. If you need fully automatic item restoration with empty inventory, only a server plugin/admin API can legitimately do it.

## Keybinds
- F6: Start / stop
- F7: Rules and MMO/SMP profile picker
- F8: Emergency stop
- K: Saved box presets

All keys are rebindable via **Options > Controls > Key Binds** (including notebook-friendly alternatives).

## Defaults
- WINGS/ปีก: mythical, weight 7
- หมวกหลัก: mythical, weight 8
- SWORD/ดาบ: mythical, weight 8
- Other weapons (including STAFF): mythical, weight 10
- Bow / crossbow: epic, weight 10
- Shield: rare, weight 10
- General armor: legendary, weight 10
- Tools: legendary, weight 10

NAME match takes priority over MATERIAL. Small Caps are normalized. Actual ExcellentCrates rarity IDs vary by server. The built-in GUI click flow for Rarity/Weight is best-effort and must be tested with one disposable item before bulk use.

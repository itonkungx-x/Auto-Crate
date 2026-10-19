## Auto-Crate v0.6.1 — Item Reward button fix

- Fixed automatic clicking of Item Reward in ExcellentCrates' `REWARD » Creation` dialog.
- Dispatches the actual Minecraft dialog Button callback instead of simulating mouse clicks at guessed screen coordinates.
- Confirms that the server returns to Crate Rewards before continuing; never reports success solely from a consumed mouse event.
- Adds diagnostic control labels if the expected button is not present and limits retries.
- All MMO/SMP presets, saved rule configurations, Thai UI, keybindings and GitHub auto-updater remain available.

Test with one inexpensive reward first. For a live-server mismatch, send the chat lines starting with `[Crate Auto Reward]` so button metadata can guide a precise correction.

# Milestone 24: game application foundation

This milestone replaces the vertical-slice demo application with the first
actual game build. The installed app is now **Rings of Power**, package
`com.jimmeali.ringsofpower.game`, and the build artifact is
`Rings-of-Power-Game.apk`.

## Player-facing game flow

- title screen with New Game, Continue, and original-ROM selection;
- strict verification of the supported 1 MiB USA/Europe ROM;
- two connected ROM-backed areas decoded directly from the owner-supplied ROM;
- touch, keyboard, and Bluetooth/USB controller movement;
- east/west area transitions and NPC collision;
- a persistent Sage quest interaction;
- manual save plus automatic area, position, and quest saving;
- restoration through Continue after relaunch.

The ROM remains outside the APK and repository. Android's document picker
grants the app read access, and decoded scene pixels remain in memory.

## Engineering boundary

This is the production game shell and save/progression path, not a claim that
all original locations, sprites, combat, dialogue, inventory, or quests have
already been reconstructed. Future milestones extend this same app rather than
shipping separate demos.

`GameProgressTest` verifies new-game defaults, area transitions, quest state,
invalid-area recovery, and coordinate clamping. The game APK build additionally
verifies its final package identity and signature.

# Milestone 24: experimental native application shell

This milestone records an experimental native application shell. It is not a
playable replacement for the original game. Its internal Android package is
`com.jimmeali.ringsofpower.game`; the partial APK is not published as the game.

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

This is a research shell and save/progression path, not a claim that
all original locations, sprites, combat, dialogue, inventory, or quests have
already been reconstructed. Future native milestones remain experimental until feature-complete playability is demonstrated.

`GameProgressTest` verifies new-game defaults, area transitions, quest state,
invalid-area recovery, and coordinate clamping. The game APK build additionally
verifies its final package identity and signature.

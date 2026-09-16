# Character stat console

The full-game compatibility APK adds a text console to the in-game pause menu.
It delegates live changes to LibretroDroid's `GLRetroView.setCheat` API and the
Genesis Plus GX core.

## Commands

`character stat value`

Characters: `buc`, `slash`, `feather`, `alexi`, `obliki`, `mortimer`.

Stats: `life`/`hp`, `mana`/`mp`, `maxlife`/`maxhp`,
`maxmana`/`maxmp`.

Values are decimal integers from 0 to 9999. Applied values remain locked until
`off` or `unlock` is entered. `god` locks current and maximum life and mana
for the entire party at 9999.

The addresses are scoped to the verified USA/Europe Rings of Power revision.
The console does not patch or redistribute the ROM.

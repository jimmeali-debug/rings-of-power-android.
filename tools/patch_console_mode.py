#!/usr/bin/env python3
"""Patch pinned Lemuroid sources with a Rings of Power stat console."""

from pathlib import Path
import sys


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"Expected one patch anchor in {path}: {old[:80]!r}")
    path.write_text(text.replace(old, new, 1))


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_console_mode.py LEMUROID_SOURCE")
    root = Path(sys.argv[1]).resolve()
    menu_dir = root / "lemuroid-app/src/main/java/com/swordfish/lemuroid/app/mobile/feature/gamemenu"
    shared_dir = root / "lemuroid-app/src/main/java/com/swordfish/lemuroid/app/shared"
    game_dir = shared_dir / "game"

    (menu_dir / "RingsConsoleDialog.kt").write_text(r'''package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
internal fun RingsConsoleDialog(
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var command by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rings of Power Console") },
        text = {
            Column {
                Text("Examples: buc life 500, slash mana 9999, god, off")
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Command") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = command.isNotBlank(),
                onClick = { onApply(command.trim()) },
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
''')

    (game_dir / "RingsConsoleCommands.kt").write_text(r'''package com.swordfish.lemuroid.app.shared.game

object RingsConsoleCommands {
    data class Cheat(val index: Int, val code: String)
    data class Result(val cheats: List<Cheat>, val disableAll: Boolean, val message: String)

    private data class CharacterStats(
        val label: String,
        val hp: Int,
        val mp: Int,
        val maxHp: Int,
        val maxMp: Int,
    )

    private val characters = linkedMapOf(
        "buc" to CharacterStats("Buc", 0xFF0304, 0xFF0306, 0xFF0308, 0xFF030A),
        "slash" to CharacterStats("Slash", 0xFF05A8, 0xFF05AA, 0xFF05AC, 0xFF05AE),
        "feather" to CharacterStats("Feather", 0xFF04D8, 0xFF04DA, 0xFF04DC, 0xFF04DE),
        "alexi" to CharacterStats("Alexi", 0xFF0540, 0xFF0542, 0xFF0544, 0xFF0546),
        "obliki" to CharacterStats("Obliki", 0xFF043C, 0xFF043E, 0xFF0440, 0xFF0442),
        "mortimer" to CharacterStats("Mortimer", 0xFF0644, 0xFF0646, 0xFF0648, 0xFF064A),
    )

    fun parse(raw: String): Result {
        val command = raw.trim().lowercase()
        if (command == "off" || command == "unlock") {
            return Result(emptyList(), true, "All stat locks disabled")
        }
        if (command == "god") {
            val cheats = mutableListOf<Cheat>()
            characters.values.forEachIndexed { characterIndex, stats ->
                listOf(stats.hp, stats.mp, stats.maxHp, stats.maxMp).forEachIndexed { statIndex, address ->
                    cheats += Cheat(characterIndex * 4 + statIndex, formatCode(address, 9999))
                }
            }
            return Result(cheats, false, "All party life and mana locked at 9999")
        }

        val parts = command.split(Regex("\\s+")).filter { it.isNotEmpty() }
        require(parts.size == 3) {
            "Use: character stat value — for example: buc life 500"
        }
        val characterIndex = characters.keys.indexOf(parts[0])
        require(characterIndex >= 0) {
            "Character: buc, slash, feather, alexi, obliki, or mortimer"
        }
        val stats = characters.getValue(parts[0])
        val statIndex: Int
        val address: Int
        val statLabel: String
        when (parts[1]) {
            "life", "hp" -> { statIndex = 0; address = stats.hp; statLabel = "life" }
            "mana", "mp" -> { statIndex = 1; address = stats.mp; statLabel = "mana" }
            "maxlife", "maxhp" -> { statIndex = 2; address = stats.maxHp; statLabel = "maximum life" }
            "maxmana", "maxmp" -> { statIndex = 3; address = stats.maxMp; statLabel = "maximum mana" }
            else -> throw IllegalArgumentException("Stat: life, mana, maxlife, or maxmana")
        }
        val value = parts[2].toIntOrNull()
            ?: throw IllegalArgumentException("Value must be a number from 0 to 9999")
        require(value in 0..9999) { "Value must be from 0 to 9999" }
        return Result(
            listOf(Cheat(characterIndex * 4 + statIndex, formatCode(address, value))),
            false,
            "${stats.label} $statLabel locked at $value",
        )
    }

    private fun formatCode(address: Int, value: Int): String =
        "%06X:%04X".format(address, value)
}
''')

    home = menu_dir / "GameMenuHomeScreen.kt"
    replace_once(
        home,
        "import androidx.compose.runtime.Composable\n",
        "import androidx.compose.runtime.Composable\n"
        "import androidx.compose.runtime.getValue\n"
        "import androidx.compose.runtime.mutableStateOf\n"
        "import androidx.compose.runtime.remember\n"
        "import androidx.compose.runtime.setValue\n",
    )
    replace_once(
        home,
        ") {\n    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {",
        ") {\n    var showRingsConsole by remember { mutableStateOf(false) }\n"
        "    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {\n"
        "        LemuroidSettingsMenuLink(\n"
        "            title = { Text(\"Developer Console\") },\n"
        "            icon = {\n"
        "                Icon(\n"
        "                    painterResource(R.drawable.ic_menu_settings),\n"
        "                    contentDescription = \"Developer Console\",\n"
        "                )\n"
        "            },\n"
        "            onClick = { showRingsConsole = true },\n"
        "        )",
    )
    tail = "    }\n}\n"
    if not home.read_text().endswith(tail):
        raise SystemExit("Game menu tail anchor not found")
    text = home.read_text()
    console = r'''    }
    if (showRingsConsole) {
        RingsConsoleDialog(
            onDismiss = { showRingsConsole = false },
            onApply = { command ->
                onResult { putExtra(GameMenuContract.RESULT_RING_CONSOLE_COMMAND, command) }
            },
        )
    }
}
'''
    home.write_text(text[: -len(tail)] + console)

    contract = shared_dir / "GameMenuContract.kt"
    replace_once(
        contract,
        '    const val RESULT_CHANGE_TILT_CONFIG = "RESULT_CHANGE_TILT_CONFIG"\n',
        '    const val RESULT_CHANGE_TILT_CONFIG = "RESULT_CHANGE_TILT_CONFIG"\n'
        '    const val RESULT_RING_CONSOLE_COMMAND = "RESULT_RING_CONSOLE_COMMAND"\n',
    )

    activity = game_dir / "BaseGameActivity.kt"
    marker = "    override fun onActivityResult(\n"
    method = r'''    private fun applyRingsConsoleCommand(command: String) {
        val retroView = baseGameScreenViewModel.retroGameView.retroGameView
        if (retroView == null) {
            displayToast("Console is not ready")
            return
        }
        runCatching {
            val result = RingsConsoleCommands.parse(command)
            if (result.disableAll) {
                repeat(24) { index -> retroView.setCheat(index, false, "FF0000:0000") }
            } else {
                result.cheats.forEach { cheat ->
                    retroView.setCheat(cheat.index, true, cheat.code)
                }
            }
            displayToast(result.message)
        }.onFailure { failure ->
            displayToast(failure.message ?: "Invalid console command")
        }
    }

'''
    replace_once(activity, marker, method + marker)
    result_anchor = "            if (data?.getBooleanExtra(GameMenuContract.RESULT_RESET, false) == true) {\n"
    console_result = (
        "            if (data?.hasExtra(GameMenuContract.RESULT_RING_CONSOLE_COMMAND) == true) {\n"
        "                applyRingsConsoleCommand(\n"
        "                    data.getStringExtra(GameMenuContract.RESULT_RING_CONSOLE_COMMAND) ?: \"\",\n"
        "                )\n"
        "            }\n"
    )
    replace_once(activity, result_anchor, console_result + result_anchor)


if __name__ == "__main__":
    main()

# GitHub Actions build trigger for alpha 5.

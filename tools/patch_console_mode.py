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
                Text("Examples: buc life 500, slash mana 500, gold 30000, god, off. Void protection is temporarily disabled for safe startup.")
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
            return Result(emptyList(), true, "All console cheats disabled")
        }
        if (command.startsWith("gold ")) {
            val parts = command.split(Regex("\\s+")).filter { it.isNotEmpty() }
            require(parts.size == 2) { "Use: gold value — for example: gold 30000" }
            val value = parts[1].toIntOrNull()
                ?: throw IllegalArgumentException("Gold must be a number from 0 to 32767")
            require(value in 0..32767) { "Gold must be from 0 to 32767" }
            return Result(
                listOf(Cheat(25, formatCode(0xFF02DE, value))),
                false,
                "Gold locked at $value",
            )
        }
        if (command == "god") {
            return Result(
                listOf(
                    Cheat(26, "AHCA-EAG0"),
                    Cheat(27, "AK8T-GA82"),
                ),
                false,
                "Safe infinite health and MP enabled",
            )
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
            ?: throw IllegalArgumentException("Value must be a number from 0 to 999")
        require(value in 0..999) { "Value must be from 0 to 999" }
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
    replace_once(
        activity,
        "            baseGameScreenViewModel.loadGame(\n                applicationContext,\n                game,\n                systemCoreConfig,\n                gameLoader,\n                intent.getBooleanExtra(EXTRA_LOAD_SAVE, false),\n            )\n",
        "            baseGameScreenViewModel.loadGame(\n                applicationContext,\n                game,\n                systemCoreConfig,\n                gameLoader,\n                false, // Skip emulator auto-resume snapshots; cartridge SRAM still loads.\n            )\n",
    )
    marker = "    override fun onActivityResult(\n"
    method = r'''    private val ringsConsoleCodes = mutableMapOf<Int, String>()

    private fun applyRingsConsoleCommand(command: String) {
        val retroView = baseGameScreenViewModel.retroGameView.retroGameView
        if (retroView == null) {
            displayToast("Console is not ready")
            return
        }
        runCatching {
            val result = RingsConsoleCommands.parse(command)
            if (result.disableAll) {
                ringsConsoleCodes.forEach { (index, code) ->
                    retroView.setCheat(index, false, code)
                }
                ringsConsoleCodes.clear()
            } else {
                result.cheats.forEach { cheat ->
                    ringsConsoleCodes[cheat.index]?.let { previousCode ->
                        retroView.setCheat(cheat.index, false, previousCode)
                    }
                    retroView.setCheat(cheat.index, true, cheat.code)
                    ringsConsoleCodes[cheat.index] = cheat.code
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

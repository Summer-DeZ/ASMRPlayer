package io.github.summerdez.asmrplayer.ui.theme

class AppThemePalette private constructor(
    val light: Boolean,
    val accent: Int,
    val switchOn: Int,
    val switchOff: Int,
    val bg: Int,
    val gray6: Int,
    val gray5: Int,
    val gray4: Int,
    val gray3: Int,
    val label: Int,
    val label2: Int,
    val label3: Int,
    val separator: Int,
    val barMaterial: Int,
    val solidBar: Int,
    val sheetMaterial: Int,
) {
    companion object {
        fun dark(): AppThemePalette {
            return AppThemePalette(
                false,
                0xFFF0936A.toInt(),
                0xFFF0936A.toInt(),
                0xFF313135.toInt(),
                0xFF0A0A0B.toInt(),
                0xFF0F0F11.toInt(),
                0xFF242427.toInt(),
                0xFF313135.toInt(),
                0xFF45454B.toInt(),
                0xFFF5F5F7.toInt(),
                0xFFB6B6BD.toInt(),
                0xFF65656C.toInt(),
                0x0FFFFFFF,
                0xBD0A0A0B.toInt(),
                0xFF0A0A0B.toInt(),
                0xFF0F0F11.toInt(),
            )
        }

        fun light(): AppThemePalette {
            return AppThemePalette(
                true,
                0xFFE27A52.toInt(),
                0xFFE27A52.toInt(),
                0xFFE2E2E6.toInt(),
                0xFFF5F5F7.toInt(),
                0xFFFFFFFF.toInt(),
                0xFFE2E2E6.toInt(),
                0xFFB6B6BD.toInt(),
                0xFF8A8A92.toInt(),
                0xFF0F0F11.toInt(),
                0xFF45454B.toInt(),
                0xFF8A8A92.toInt(),
                0x1F0A0A0B,
                0xEEF5F5F7.toInt(),
                0xFFF5F5F7.toInt(),
                0xF2FFFFFF.toInt(),
            )
        }
    }
}

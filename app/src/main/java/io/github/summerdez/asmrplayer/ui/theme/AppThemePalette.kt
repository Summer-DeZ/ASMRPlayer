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
                0xFF8E8E93.toInt(),
                0xFFD24E7D.toInt(),
                0xFF303133.toInt(),
                0xFF0A0B0D.toInt(),
                0xFF17181A.toInt(),
                0xFF242528.toInt(),
                0xFF2D2E30.toInt(),
                0xFF3A3B3D.toInt(),
                0xFFF5F5F7.toInt(),
                0xFFB6B6BD.toInt(),
                0xFF65656C.toInt(),
                0xFF2D2E30.toInt(),
                0xE6000000.toInt(),
                0xFF0A0B0D.toInt(),
                0xFF17181A.toInt(),
            )
        }

        fun light(): AppThemePalette {
            return AppThemePalette(
                true,
                0xFF6E6E73.toInt(),
                0xFFFF679A.toInt(),
                0xFFE5E5E5.toInt(),
                0xFFF2F2F2.toInt(),
                0xFFFFFFFF.toInt(),
                0xFFE5E5E5.toInt(),
                0xFFDADADF.toInt(),
                0xFF8A8A92.toInt(),
                0xFF323232.toInt(),
                0xFF45454B.toInt(),
                0xFF8A8A92.toInt(),
                0xFFE5E5E5.toInt(),
                0xF2FFFFFF.toInt(),
                0xFFF2F2F2.toInt(),
                0xF7F7F7F8.toInt(),
            )
        }
    }
}

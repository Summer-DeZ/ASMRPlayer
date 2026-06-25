package io.github.summerdez.asmrplayer.domain.model

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM;

    companion object {
        fun fromName(name: String?): AppThemeMode {
            if (name == null) {
                return DARK
            }
            for (mode in values()) {
                if (mode.name.equals(name, ignoreCase = true)) {
                    return mode
                }
            }
            return DARK
        }
    }
}

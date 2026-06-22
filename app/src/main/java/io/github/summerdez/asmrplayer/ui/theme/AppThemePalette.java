package io.github.summerdez.asmrplayer.ui.theme;

import io.github.summerdez.asmrplayer.R;
import io.github.summerdez.asmrplayer.data.*;
import io.github.summerdez.asmrplayer.data.remote.*;
import io.github.summerdez.asmrplayer.data.download.*;
import io.github.summerdez.asmrplayer.data.files.*;
import io.github.summerdez.asmrplayer.domain.*;
import io.github.summerdez.asmrplayer.domain.model.*;
import io.github.summerdez.asmrplayer.playback.*;
import io.github.summerdez.asmrplayer.presentation.*;
import io.github.summerdez.asmrplayer.ui.*;
import io.github.summerdez.asmrplayer.ui.activity.*;
import io.github.summerdez.asmrplayer.ui.components.*;
import io.github.summerdez.asmrplayer.ui.screens.*;
import io.github.summerdez.asmrplayer.ui.theme.*;
import io.github.summerdez.asmrplayer.ui.util.*;
import io.github.summerdez.asmrplayer.di.*;
final class AppThemePalette {
    final boolean light;
    final int accent;
    final int switchOn;
    final int switchOff;
    final int bg;
    final int gray6;
    final int gray5;
    final int gray4;
    final int gray3;
    final int label;
    final int label2;
    final int label3;
    final int separator;
    final int barMaterial;
    final int solidBar;
    final int sheetMaterial;

    private AppThemePalette(
            boolean light,
            int accent,
            int switchOn,
            int switchOff,
            int bg,
            int gray6,
            int gray5,
            int gray4,
            int gray3,
            int label,
            int label2,
            int label3,
            int separator,
            int barMaterial,
            int solidBar,
            int sheetMaterial) {
        this.light = light;
        this.accent = accent;
        this.switchOn = switchOn;
        this.switchOff = switchOff;
        this.bg = bg;
        this.gray6 = gray6;
        this.gray5 = gray5;
        this.gray4 = gray4;
        this.gray3 = gray3;
        this.label = label;
        this.label2 = label2;
        this.label3 = label3;
        this.separator = separator;
        this.barMaterial = barMaterial;
        this.solidBar = solidBar;
        this.sheetMaterial = sheetMaterial;
    }

    static AppThemePalette dark() {
        return new AppThemePalette(
                false,
                0xFFE0A26B,
                0xFFE0A26B,
                0xFF3A322A,
                0xFF17120E,
                0xFF221A14,
                0xFF2C231B,
                0xFF3A322A,
                0xFF4A3B2E,
                0xFFF6F1EA,
                0x8CF6F1EA,
                0x4DF6F1EA,
                0x29BEA078,
                0xDB211A14,
                0xFF1C1610,
                0xF0261E16);
    }

    static AppThemePalette light() {
        return new AppThemePalette(
                true,
                0xFFC47A3E,
                0xFFC47A3E,
                0xFFE2D8C8,
                0xFFFBF6EF,
                0xFFFFFFFF,
                0xFFEBE0D0,
                0xFFE2D8C8,
                0xFFD0C2B0,
                0xFF2A2018,
                0x943C2D1E,
                0x523C2D1E,
                0x29785A3C,
                0xE6FBF6EF,
                0xFFF7F1E8,
                0xF2FAF5ED);
    }
}

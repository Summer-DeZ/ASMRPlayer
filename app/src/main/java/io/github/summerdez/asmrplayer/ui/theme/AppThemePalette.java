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
                0xFFF0936A,
                0xFFF0936A,
                0xFF313135,
                0xFF0A0A0B,
                0xFF0F0F11,
                0xFF242427,
                0xFF313135,
                0xFF45454B,
                0xFFF5F5F7,
                0xFFB6B6BD,
                0xFF65656C,
                0x0FFFFFFF,
                0xBD0A0A0B,
                0xFF0A0A0B,
                0xFF0F0F11);
    }

    static AppThemePalette light() {
        return new AppThemePalette(
                true,
                0xFFE27A52,
                0xFFE27A52,
                0xFFE2E2E6,
                0xFFF5F5F7,
                0xFFFFFFFF,
                0xFFE2E2E6,
                0xFFB6B6BD,
                0xFF8A8A92,
                0xFF0F0F11,
                0xFF45454B,
                0xFF8A8A92,
                0x1F0A0A0B,
                0xEEF5F5F7,
                0xFFF5F5F7,
                0xF2FFFFFF);
    }
}

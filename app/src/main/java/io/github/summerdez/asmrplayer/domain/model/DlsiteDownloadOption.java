package io.github.summerdez.asmrplayer.domain.model;

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
import java.util.ArrayList;
import java.util.List;

public final class DlsiteDownloadOption {
    public final String id;
    public final String title;
    public final List<DlsiteJsonParser.ContentFile> audioFiles;

    public DlsiteDownloadOption(String id, String title, List<DlsiteJsonParser.ContentFile> audioFiles) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.audioFiles = audioFiles == null ? new ArrayList<>() : new ArrayList<>(audioFiles);
    }

    public String dialogLabel() {
        return title + " · " + audioFiles.size() + " 首";
    }
}

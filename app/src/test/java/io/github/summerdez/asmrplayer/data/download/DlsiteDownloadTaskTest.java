package io.github.summerdez.asmrplayer.data.download;

import static org.junit.Assert.assertEquals;

import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption;
import io.github.summerdez.asmrplayer.data.remote.DlsiteJsonParser;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class DlsiteDownloadTaskTest {
    @Test
    public void progressTrackerAggregatesMultipleFileProgress() {
        DlsiteJsonParser.ContentFile first = contentFile("01.mp3", 100L);
        DlsiteJsonParser.ContentFile second = contentFile("02.mp3", 300L);
        DlsiteDownloadOption option = new DlsiteDownloadOption("main", "Main", Arrays.asList(first, second));
        DlsiteDownloadTask.DownloadProgressTracker tracker = new DlsiteDownloadTask.DownloadProgressTracker(
                Collections.singletonList(option));

        tracker.onFileProgress(option, first, 100L, 100L);
        DlsiteDownloadTask.TaskProgress progress = tracker.onFileProgress(option, second, 150L, 300L);

        assertEquals(250L, progress.bytesDownloaded);
        assertEquals(400L, progress.totalBytes);
    }

    @Test
    public void progressTrackerDoesNotPublishPercentWhenLengthIsUnknown() {
        DlsiteJsonParser.ContentFile unknown = contentFile("unknown.mp3", 0L);
        DlsiteDownloadOption option = new DlsiteDownloadOption("main", "Main", Collections.singletonList(unknown));
        DlsiteDownloadTask.DownloadProgressTracker tracker = new DlsiteDownloadTask.DownloadProgressTracker(
                Collections.singletonList(option));

        DlsiteDownloadTask.TaskProgress progress = tracker.onFileProgress(option, unknown, 150L, 300L);

        assertEquals(150L, progress.bytesDownloaded);
        assertEquals(-1L, progress.totalBytes);
    }

    @Test
    public void contentResultsMapTrackIdsAfterSingleImport() {
        File root = new File("/tmp/dlsite-import-test");
        File mainOne = new File(root, "main/01.wav");
        File mainTwo = new File(root, "main/02.wav");
        File bonusOne = new File(root, "bonus/01.wav");
        DlsiteDownloadTask.DownloadedContent main = new DlsiteDownloadTask.DownloadedContent(
                new DlsiteDownloadOption("main", "Main", Collections.emptyList()),
                new File(root, "main"),
                Arrays.asList(mainOne, mainTwo));
        DlsiteDownloadTask.DownloadedContent bonus = new DlsiteDownloadTask.DownloadedContent(
                new DlsiteDownloadOption("bonus", "Bonus", Collections.emptyList()),
                new File(root, "bonus"),
                Collections.singletonList(bonusOne));
        Map<String, String> trackIdsByPath = new HashMap<>();
        trackIdsByPath.put(mainOne.getAbsolutePath(), "track-main-1");
        trackIdsByPath.put(mainTwo.getAbsolutePath(), "track-main-2");
        trackIdsByPath.put(bonusOne.getAbsolutePath(), "track-bonus-1");

        List<DlsiteDownloadTask.ContentResult> results = DlsiteDownloadTask.contentResultsForImport(
                Arrays.asList(main, bonus),
                new DlsiteDownloadTask.ImportResult(
                        "playlist-1",
                        Arrays.asList("track-main-1", "track-main-2", "track-bonus-1"),
                        3,
                        trackIdsByPath));

        assertEquals(2, results.size());
        assertEquals("main", results.get(0).optionId);
        assertEquals(Arrays.asList("track-main-1", "track-main-2"), results.get(0).trackIds);
        assertEquals(2, results.get(0).trackCount);
        assertEquals("bonus", results.get(1).optionId);
        assertEquals(Collections.singletonList("track-bonus-1"), results.get(1).trackIds);
        assertEquals(1, results.get(1).trackCount);
    }

    private static DlsiteJsonParser.ContentFile contentFile(String name, long lengthBytes) {
        return new DlsiteJsonParser.ContentFile(
                name,
                name,
                "optimized/" + name,
                "",
                name + ".vtt",
                lengthBytes);
    }
}

package de.maiu.anticheat.ai;

import de.maiu.anticheat.MaiuAntiCheat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Continuous Learning Collector: Saves live combat feature windows alongside ground truth / verdicts.
 * Enables retraining or fine-tuning models against actual server telemetry (MLSAC dataset approach).
 */
public class TrainingDataCollector {

    private final MaiuAntiCheat plugin;
    private final File datasetFile;
    private final ExecutorService diskQueue = Executors.newSingleThreadExecutor();

    public TrainingDataCollector(MaiuAntiCheat plugin) {
        this.plugin = plugin;
        File folder = new File(plugin.getDataFolder(), "ai_dataset");
        if (!folder.exists()) folder.mkdirs();
        this.datasetFile = new File(folder, "combat_telemetry.jsonl");
    }

    public void record(final String featureJson, final String verdict, final double confidence) {
        diskQueue.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedWriter w = new BufferedWriter(new FileWriter(datasetFile, true));
                    // Formats line as JSONL
                    String line = String.format(java.util.Locale.ROOT,
                            "{\"timestamp\":%d,\"features\":%s,\"label\":\"%s\",\"confidence\":%.2f}",
                            System.currentTimeMillis(), featureJson, verdict, confidence);
                    w.write(line);
                    w.newLine();
                    w.close();
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to record training telemetry: " + e.getMessage());
                }
            }
        });
    }

    public void shutdown() {
        diskQueue.shutdown();
    }
}

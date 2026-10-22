package de.maiu.anticheat.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.maiu.anticheat.MaiuAntiCheat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Local LLM (Ollama) used as a *second opinion* on suspicious combat data.
 * It never decides alone: the rule engine always has to agree (see CombatAnalyzer). Fully async.
 */
public class OllamaJudge {

    public static final class Verdict {
        public final boolean cheat;
        public final String type;
        public final double confidence;
        public final long latencyMs;

        Verdict(boolean cheat, String type, double confidence, long latencyMs) {
            this.cheat = cheat;
            this.type = type;
            this.confidence = confidence;
            this.latencyMs = latencyMs;
        }
    }

    public interface Callback {
        void done(Verdict verdict, String error);
    }

    private final MaiuAntiCheat plugin;
    private volatile String url;
    private volatile String model;
    private volatile int timeoutMs;
    private final ExecutorService pool = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private int n = 0;

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "MaiuAC-Judge-" + (++n));
            t.setDaemon(true);
            return t;
        }
    });

    public OllamaJudge(MaiuAntiCheat plugin, String url, String model, int timeoutMs) {
        this.plugin = plugin;
        update(url, model, timeoutMs);
    }

    public final void update(String url, String model, int timeoutMs) {
        this.url = url;
        this.model = model;
        this.timeoutMs = timeoutMs;
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    /** Loads the model into VRAM so the first real request is fast. */
    public void warmup(final Callback cb) {
        pool.submit(new Runnable() {
            @Override
            public void run() {
                long t0 = System.currentTimeMillis();
                try {
                    String r = post("{\"model\":\"" + model + "\",\"prompt\":\"{}\",\"stream\":false,\"keep_alive\":\"30m\","
                            + "\"options\":{\"num_predict\":1}}");
                    if (cb != null) cb.done(new Verdict(false, "None", 0, System.currentTimeMillis() - t0), null);
                } catch (Throwable t) {
                    if (cb != null) cb.done(null, t.getMessage());
                }
            }
        });
    }

    public void ask(final String featuresJson, final Callback cb) {
        pool.submit(new Runnable() {
            @Override
            public void run() {
                long t0 = System.currentTimeMillis();
                try {
                    JsonObject req = new JsonObject();
                    req.addProperty("model", model);
                    req.addProperty("prompt", featuresJson);
                    req.addProperty("stream", false);
                    req.addProperty("format", "json");
                    req.addProperty("keep_alive", "30m");
                    JsonObject opt = new JsonObject();
                    opt.addProperty("temperature", 0);
                    opt.addProperty("num_predict", 48);
                    opt.addProperty("num_ctx", 1024);
                    req.add("options", opt);
                    String raw = post(req.toString());
                    JsonObject root = new JsonParser().parse(raw).getAsJsonObject();
                    JsonObject v = new JsonParser().parse(root.get("response").getAsString()).getAsJsonObject();
                    String verdict = v.has("verdict") ? v.get("verdict").getAsString() : "legit";
                    String type = v.has("cheat_type") ? v.get("cheat_type").getAsString() : "None";
                    double conf = v.has("confidence") ? v.get("confidence").getAsDouble() : 0.5;
                    boolean cheat = verdict.equalsIgnoreCase("cheat");
                    cb.done(new Verdict(cheat, type, Math.max(0, Math.min(1, conf)), System.currentTimeMillis() - t0), null);
                } catch (Throwable t) {
                    cb.done(null, String.valueOf(t.getMessage()));
                }
            }
        });
    }

    private String post(String body) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url + "/api/generate").openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setConnectTimeout(2000);
            c.setReadTimeout(timeoutMs);
            c.setDoOutput(true);
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.close();
            int code = c.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } finally {
            if (c != null) c.disconnect();
        }
    }
}

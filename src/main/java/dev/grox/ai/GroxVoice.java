package dev.grox.ai;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional local text-to-speech bridge. Requires a separately installed eSpeak executable. */
public final class GroxVoice {
    private static final Pattern VILLAGER_LINE = Pattern.compile("^\\[([^\\]]{1,48})\\]\\s+(.+)$");
    private static final AtomicLong LAST_QUEUED_AT = new AtomicLong(0L);
    private static final ThreadPoolExecutor SPEECH_QUEUE = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(3),
            runnable -> {
                Thread thread = new Thread(runnable, "GroxVillagerVoice");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    private static volatile Path executable;
    private static volatile boolean initialized;
    private static volatile boolean warnedMissing;

    private GroxVoice() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        Path config = FabricLoader.getInstance().getConfigDir().resolve("groxvillagersai-voice.properties");
        Properties properties = new Properties();
        try {
            Files.createDirectories(config.getParent());
            if (Files.exists(config)) {
                try (java.io.Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            } else {
                properties.setProperty("espeak.path", "");
                properties.setProperty("enabled", "true");
                try (java.io.Writer writer = Files.newBufferedWriter(config, StandardCharsets.UTF_8)) {
                    properties.store(writer, "GroxVillagersAI local voice. Set espeak.path to espeak.exe.");
                }
            }
            if (!Boolean.parseBoolean(properties.getProperty("enabled", "true"))) return;
            String configured = properties.getProperty("espeak.path", "").trim();
            if (!configured.isEmpty()) {
                Path candidate = Path.of(configured);
                if (Files.isRegularFile(candidate)) executable = candidate;
            } else {
                executable = findDefaultExecutable();
            }
        } catch (Exception exception) {
            System.err.println("[GroxVillagersAI] Voice config error: " + exception.getMessage());
        }
    }

    private static Path findDefaultExecutable() {
        String[] candidates = {
                "C:\\Program Files (x86)\\eSpeak\\command_line\\espeak.exe",
                "C:\\Program Files\\eSpeak\\command_line\\espeak.exe",
                "C:\\Program Files (x86)\\eSpeak\\espeak.exe",
                "C:\\Program Files\\eSpeak\\espeak.exe"
        };
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) return path;
        }
        return Path.of("espeak.exe"); // Also works when eSpeak is on PATH.
    }

    public static void onGameMessage(Text message) {
        if (!initialized) initialize();
        String plain = message.getString().replaceAll("\\s+", " ").trim();
        Matcher matcher = VILLAGER_LINE.matcher(plain);
        if (!matcher.matches()) return;
        String spoken = matcher.group(2).trim();
        if (spoken.isEmpty() || spoken.length() > 240) return;

        long now = System.currentTimeMillis();
        long previous = LAST_QUEUED_AT.get();
        if (now - previous < 650L || !LAST_QUEUED_AT.compareAndSet(previous, now)) return;
        SPEECH_QUEUE.execute(() -> speak(spoken));
    }

    private static void speak(String text) {
        if (executable == null || (!Files.isRegularFile(executable) && executable.getNameCount() > 1)) {
            if (!warnedMissing) {
                warnedMissing = true;
                System.err.println("[GroxVillagersAI] eSpeak not found. Set espeak.path in config/groxvillagersai-voice.properties.");
            }
            return;
        }

        Path wav = null;
        try {
            wav = Files.createTempFile("grox-villager-", ".wav");
            Process process = new ProcessBuilder(
                    executable.toString(), "-v", voiceFor(text), "-s", "145", "-p", "35",
                    "-w", wav.toAbsolutePath().toString(), text
            ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return;
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(wav) || Files.size(wav) < 44L) return;
            playWav(wav);
            wav = null;
        } catch (Exception exception) {
            System.err.println("[GroxVillagersAI] Voice playback failed: " + exception.getMessage());
        } finally {
            if (wav != null) {
                try { Files.deleteIfExists(wav); } catch (IOException ignored) {}
            }
        }
    }

    private static String voiceFor(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) return "ar+croak";
            if (c >= 0x0900 && c <= 0x097F) return "hi+croak";
            if (c >= 0x3040 && c <= 0x30FF) return "ja+croak";
            if (c >= 0x4E00 && c <= 0x9FFF) return "zh+croak";
            if (c >= 0x0400 && c <= 0x04FF) return "ru+croak";
        }
        return "en+croak";
    }

    private static void playWav(Path wav) throws Exception {
        AudioInputStream stream = AudioSystem.getAudioInputStream(wav.toFile());
        Clip clip = AudioSystem.getClip();
        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
                clip.close();
                try { Files.deleteIfExists(wav); } catch (IOException ignored) {}
            }
        });
        try {
            clip.open(stream);
            clip.start();
        } finally {
            stream.close();
        }
    }
}

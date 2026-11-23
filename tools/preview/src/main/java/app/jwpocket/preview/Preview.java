package app.jwpocket.preview;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Runs the real JW Pocket phone app on this computer, inside the Android
 * emulator.
 *
 *   java -jar jw-pocket-preview.jar                      opens it in an emulator window
 *   java -jar jw-pocket-preview.jar --screenshot x.png   runs it out of sight, saves a picture, exits
 *
 * Uses a phone already connected over adb when there is one; otherwise starts
 * an emulator (the project's own in .avd/, else the first one installed).
 */
public final class Preview {

    private static final String PACKAGE = "app.jwpocket";
    private static final String ACTIVITY = PACKAGE + "/app.jwpocket.phone.MainActivity";

    public static void main(String[] args) throws Exception {
        String screenshot = null;
        for (int i = 0; i < args.length; i++) {
            if ("--screenshot".equals(args[i]) && i + 1 < args.length) screenshot = args[++i];
        }
        File project = projectDir();
        File sdk = sdkDir(project);
        File adb = new File(sdk, "platform-tools/adb");
        File apk = new File(project, "app-phone/build/outputs/apk/debug/app-phone-debug.apk");
        if (!apk.exists()) fail("Build the app first (gradle assemble): " + apk);
        if (!adb.exists()) fail("Android platform-tools not found in " + sdk);

        Process emulator = null;
        String serial = connectedDevice(adb);
        if (serial == null) {
            emulator = startEmulator(sdk, project, screenshot != null);
            serial = waitForDevice(adb, emulator);
        }
        try {
            run(adb.getPath(), "-s", serial, "install", "-r", apk.getPath());
            run(adb.getPath(), "-s", serial, "shell", "am", "start", "-n", ACTIVITY);
            if (screenshot == null) {
                System.out.println("JW Pocket is running in the emulator. Close the emulator window to quit.");
                if (emulator != null) emulator.waitFor();
                return;
            }
            // Let the app load the library and draw its pictures (about 120 frames and then some).
            Thread.sleep(20_000);
            byte[] png = capture(adb, serial);
            File out = new File(screenshot).getAbsoluteFile();
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            try (OutputStream os = new FileOutputStream(out)) { os.write(png); }
            System.out.println("Saved " + out + " (" + png.length + " bytes)");
        } finally {
            if (emulator != null && screenshot != null) {
                emulator.destroy();
                if (!emulator.waitFor(15, TimeUnit.SECONDS)) emulator.destroyForcibly();
            }
        }
    }

    /** The project folder: two levels above build/libs where the jar lives, or the current folder. */
    private static File projectDir() {
        try {
            File jar = new File(Preview.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File candidate = jar.getParentFile().getParentFile().getParentFile();
            if (new File(candidate, "settings.gradle.kts").exists()) return candidate;
        } catch (Exception ignored) { }
        return new File(".").getAbsoluteFile();
    }

    private static File sdkDir(File project) throws IOException {
        for (String env : new String[] {"ANDROID_HOME", "ANDROID_SDK_ROOT"}) {
            String v = System.getenv(env);
            if (v != null && new File(v).isDirectory()) return new File(v);
        }
        File props = new File(project, "local.properties");
        if (props.exists()) {
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(props)) { p.load(in); }
            String dir = p.getProperty("sdk.dir");
            if (dir != null && new File(dir).isDirectory()) return new File(dir);
        }
        return new File(System.getProperty("user.home"), "Android/Sdk");
    }

    private static String connectedDevice(File adb) throws Exception {
        for (String line : output(adb.getPath(), "devices").split("\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2 && parts[1].equals("device")) return parts[0];
        }
        return null;
    }

    private static Process startEmulator(File sdk, File project, boolean hidden) throws Exception {
        File emulator = new File(sdk, "emulator/emulator");
        if (!emulator.exists()) fail("No phone connected and no Android emulator installed in " + sdk);
        File avdHome = new File(project, ".avd");
        String avd = null;
        if (new File(avdHome, "pocket_phone.ini").exists()) avd = "pocket_phone";
        else {
            avdHome = null;
            for (String line : output(emulator.getPath(), "-list-avds").split("\n")) {
                if (!line.isBlank() && !line.startsWith("INFO")) { avd = line.trim(); break; }
            }
        }
        if (avd == null) fail("No emulator found. Create one in Android Studio's Device Manager.");
        List<String> cmd = new ArrayList<>(List.of(emulator.getPath(), "-avd", avd, "-no-audio",
            "-no-snapshot-save", "-no-boot-anim", "-gpu", hidden ? "guest" : "auto"));
        if (hidden) cmd.add("-no-window");
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.environment().put("ANDROID_SDK_ROOT", sdk.getPath());
        if (avdHome != null) pb.environment().put("ANDROID_AVD_HOME", avdHome.getPath());
        System.out.println("Starting the Android emulator (" + avd + ")…");
        return pb.start();
    }

    private static String waitForDevice(File adb, Process emulator) throws Exception {
        long deadline = System.currentTimeMillis() + 300_000;
        while (System.currentTimeMillis() < deadline) {
            if (!emulator.isAlive()) fail("The emulator stopped while starting.");
            String serial = connectedDevice(adb);
            if (serial != null && "1".equals(output(adb.getPath(), "-s", serial, "shell", "getprop", "sys.boot_completed").trim())) {
                Thread.sleep(5_000);
                return serial;
            }
            Thread.sleep(2_000);
        }
        fail("The emulator took too long to start.");
        return null;
    }

    private static byte[] capture(File adb, String serial) throws Exception {
        Process p = new ProcessBuilder(adb.getPath(), "-s", serial, "exec-out", "screencap", "-p")
            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        byte[] png = p.getInputStream().readAllBytes();
        p.waitFor();
        if (png.length < 1000) fail("The screenshot came back empty.");
        return png;
    }

    private static void run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) fail(String.join(" ", cmd) + " failed:\n" + out);
    }

    private static String output(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return out;
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}

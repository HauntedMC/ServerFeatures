package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeTask;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class DefaultConfigsSanitizeTask implements SanitizeTask {

    private static final Charset UTF8 = StandardCharsets.UTF_8;

    private static final LinkedHashMap<String, String> REQUIRED = new LinkedHashMap<>();
    static {
        REQUIRED.put("banned-ips.json", "[]");
        REQUIRED.put("banned-players.json", "[]");
        REQUIRED.put("eula.txt", "eula=true");
        REQUIRED.put("ops.json", "[]");
        REQUIRED.put("permissions.yml", "");
        REQUIRED.put("whitelist.json", "[]");
    }

    @Override
    public String name() {
        return "DefaultConfigs";
    }

    @Override
    public SanitizeResult run(SanitizeContext ctx) throws IOException {
        Path root = ctx.serverRoot().normalize();

        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (Map.Entry<String, String> e : REQUIRED.entrySet()) {
            String fileName = e.getKey();
            String expected = e.getValue();
            Path target = root.resolve(fileName).normalize();

            try {
                Path parent = target.getParent();
                if (parent != null) Files.createDirectories(parent);

                boolean exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);

                if (exists) {
                    String actual = readNormalized(target);
                    String expNorm = normalize(expected);

                    if (actual.equals(expNorm)) {
                        unchanged.add(fileName);
                        continue;
                    }
                    writeExact(target, expected);
                    updated.add(fileName);
                } else {
                    writeExact(target, expected);
                    created.add(fileName);
                }
            } catch (Throwable ex) {
                failed.add(fileName);
            }
        }

        StringBuilder sb = new StringBuilder();
        if (!created.isEmpty())  sb.append("created: ").append(String.join(", ", created)).append("; ");
        if (!updated.isEmpty())  sb.append("updated: ").append(String.join(", ", updated)).append("; ");
        if (!unchanged.isEmpty()) sb.append("unchanged: ").append(String.join(", ", unchanged)).append("; ");
        if (!failed.isEmpty())   sb.append("failed: ").append(String.join(", ", failed)).append("; ");

        boolean changed = !created.isEmpty() || !updated.isEmpty() || !failed.isEmpty();
        return changed
                ? SanitizeResult.changed(sb.toString().trim())
                : SanitizeResult.unchanged("All default configs already consistent. " + sb.toString().trim());
    }

    private static String readNormalized(Path p) throws IOException {
        String s = Files.readString(p, UTF8);
        return normalize(s);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = s.replace("\r\n", "\n").replace("\r", "\n");
        return n.replaceAll("[\\s\\n\\r]+$", "");
    }

    private static void writeExact(Path p, String expected) throws IOException {
        String toWrite = expected;
        if (!expected.isEmpty()) {
            toWrite = expected + "\n";
        }
        Files.writeString(p, toWrite, UTF8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}

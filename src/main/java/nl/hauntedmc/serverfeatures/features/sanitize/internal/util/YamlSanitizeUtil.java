package nl.hauntedmc.serverfeatures.features.sanitize.internal.util;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

public final class YamlSanitizeUtil {
    private YamlSanitizeUtil() {}

    /* ---------- YAML dumper ---------- */

    public static DumperOptions defaultDumperOptions() {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        opts.setIndent(2);
        opts.setIndicatorIndent(1); // must be < indent
        opts.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        opts.setSplitLines(false);
        opts.setWidth(10_000);
        return opts;
    }

    public static Yaml newYaml() {
        return new Yaml(defaultDumperOptions());
    }

    /* ---------- Map/List shaping ---------- */

    public static LinkedHashMap<String, Object> toLinkedMap(Map<?, ?> in) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : in.entrySet()) {
            String k = String.valueOf(e.getKey());
            Object v = e.getValue();
            if (v instanceof Map<?, ?> m) out.put(k, toLinkedMap(m));
            else if (v instanceof List<?> l) out.put(k, new ArrayList<>(l));
            else out.put(k, v);
        }
        return out;
    }

    public static Object cloneForYaml(Object v) {
        if (v instanceof Map<?, ?> m) return toLinkedMap(m);
        if (v instanceof List<?> l)  return new ArrayList<>(l);
        return v;
    }

    /* ---------- Section helpers ---------- */

    public static Map<String, Object> getOrCreateSection(Map<String, Object> parent, String key) {
        Object o = parent.get(key);
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> linked = toLinkedMap(m);
            if (o != linked) parent.put(key, linked);
            return linked;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        parent.put(key, created);
        return created;
    }

    public static Map<String, Object> ensureSubMap(Map<String, Object> parent, String key) {
        Object next = parent.get(key);
        if (!(next instanceof Map<?, ?>)) {
            Map<String, Object> created = new LinkedHashMap<>();
            parent.put(key, created);
            return created;
        }
        Map<String, Object> linked = toLinkedMap((Map<?, ?>) next);
        if (next != linked) parent.put(key, linked);
        return linked;
    }

    /* ---------- Set/get by dotted path ---------- */

    public static void set(Map<String, Object> root, String dottedPath, Object value) {
        String[] parts = dottedPath.split("\\.");
        Map<String, Object> m = root;
        for (int i = 0; i < parts.length - 1; i++) {
            m = ensureSubMap(m, parts[i]);
        }
        String leaf = parts[parts.length - 1];
        Object want = cloneForYaml(value);
        Object cur  = m.get(leaf);
        if (!Objects.equals(cur, want)) {
            m.put(leaf, want);
        }
    }

    public static void ensureExactList(Map<String, Object> root, String dottedPath, List<?> desired) {
        String[] parts = dottedPath.split("\\.");
        Map<String, Object> m = root;
        for (int i = 0; i < parts.length - 1; i++) {
            m = ensureSubMap(m, parts[i]);
        }
        String leaf = parts[parts.length - 1];
        Object cur = m.get(leaf);
        if (cur instanceof List<?> l && l.size() == desired.size()) {
            for (int i = 0; i < l.size(); i++) {
                if (!Objects.equals(l.get(i), desired.get(i))) {
                    m.put(leaf, new ArrayList<>(desired));
                    return;
                }
            }
            return;
        }
        m.put(leaf, new ArrayList<>(desired));
    }

    public static void ensureSectionWithValues(Map<String, Object> root, String key, Map<String, Object> enforced) {
        Map<String, Object> sec = getOrCreateSection(root, key);
        for (Map.Entry<String, Object> e : enforced.entrySet()) {
            Object cur = sec.get(e.getKey());
            Object want = cloneForYaml(e.getValue());
            if (!Objects.equals(cur, want)) {
                sec.put(e.getKey(), want);
            }
        }
    }

    public static Map<String, Object> mapOf(String k, Object v) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    /* ---------- Normalization ---------- */

    public static String normalize(String s) {
        if (s == null) return "";
        String n = s.replace("\r\n", "\n").replace("\r", "\n");
        return n.replaceAll("[\\s\\n\\r]+$", "");
    }

    /* ---------- Inline control comment annotation ---------- */

    public static String appendControlComments(String dumped, Collection<String> controlledPaths) {
        Set<String> controlled = new HashSet<>(controlledPaths);
        List<String> out = new ArrayList<>();
        List<String> stack = new ArrayList<>();
        String[] lines = dumped.split("\\r?\\n", -1);
        final int INDENT = 2;

        for (String line : lines) {
            String orig = line;
            String t = lstrip(line);
            if (t.isEmpty() || t.startsWith("#")) {
                out.add(orig);
                continue;
            }
            if (t.startsWith("- ")) { // list item
                out.add(orig);
                continue;
            }

            int spaces = line.length() - t.length();
            int level = Math.max(0, spaces / INDENT);
            String key = extractKeyToken(t);
            if (key != null) {
                // fix stack to current level
                while (stack.size() > level) stack.remove(stack.size() - 1);
                if (stack.size() < level) {
                    // malformed indent jump -> reset
                    stack.clear();
                }
                stack.add(key);
                String dotted = String.join(".", stack);
                if (controlled.contains(dotted) && orig.indexOf('#') == -1) {
                    orig = orig + " # controlled by Sanitize";
                }
                // if leaf "key: value" -> pop; otherwise "key:" keeps nested
                if (isLeafKeyValue(t)) {
                    stack.remove(stack.size() - 1);
                }
            }

            out.add(orig);
        }
        String joined = String.join("\n", out);
        if (dumped.endsWith("\n") && !joined.endsWith("\n")) joined += "\n";
        return joined;
    }

    private static String lstrip(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') i++;
        return s.substring(i);
    }

    private static String extractKeyToken(String trimmedLine) {
        boolean inS = false, inD = false;
        int idx = -1;
        for (int i = 0; i < trimmedLine.length(); i++) {
            char c = trimmedLine.charAt(i);
            if (c == '\'' && !inD) inS = !inS;
            else if (c == '"' && !inS) inD = !inD;
            else if (c == ':' && !inS && !inD) { idx = i; break; }
        }
        if (idx <= 0) return null;
        String key = trimmedLine.substring(0, idx).trim();
        if (key.startsWith("-") || key.startsWith("?")) return null; // list or complex key
        if ((key.startsWith("'") && key.endsWith("'")) || (key.startsWith("\"") && key.endsWith("\""))) {
            key = key.substring(1, key.length() - 1);
        }
        return key;
    }

    private static boolean isLeafKeyValue(String trimmedLine) {
        int colon = -1; boolean inS=false, inD=false;
        for (int i = 0; i < trimmedLine.length(); i++) {
            char c = trimmedLine.charAt(i);
            if (c == '\'' && !inD) inS = !inS;
            else if (c == '"' && !inS) inD = !inD;
            else if (c == ':' && !inS && !inD) { colon = i; break; }
        }
        if (colon < 0) return false;
        String after = trimmedLine.substring(colon + 1).trim();
        return !after.isEmpty(); // "key: value" vs "key:"
    }
}

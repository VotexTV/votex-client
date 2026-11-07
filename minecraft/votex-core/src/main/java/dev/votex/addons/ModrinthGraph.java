package dev.votex.addons;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;

/** Resolves metadata before any download or queue mutation. Required dependencies stay mandatory. */
public final class ModrinthGraph {
    private ModrinthGraph() {}

    public interface Catalog {
        JsonArray versions(String project, String kind) throws Exception;
        JsonObject version(String id) throws Exception;
    }

    public static final class Rejected extends IOException {
        public Rejected(String message) { super(message); }
    }

    public static LinkedHashMap<String, JsonObject> resolve(String id, String kind, JsonArray installed, Catalog catalog) throws Exception {
        AddonRules.reference(id);
        AddonRules.folder(kind);
        var candidates = ordered(catalog.versions(id, kind));
        Rejected last = new Rejected("Keine kompatible Minecraft-26.2-Version gefunden: " + id);
        for (var candidate : candidates.subList(0, Math.min(16, candidates.size()))) {
            try { return exact(candidate, kind, installed, catalog); }
            catch (Rejected incompatible) { last = incompatible; }
        }
        throw new Rejected("Keine vollständig kompatible Version für " + id + " gefunden. " + last.getMessage());
    }

    private record Reference(String id, boolean pinned, String kind) {}

    private static LinkedHashMap<String, JsonObject> exact(JsonObject root, String kind, JsonArray installed, Catalog catalog) throws Exception {
        var plan = new LinkedHashMap<String, JsonObject>();
        var queue = new ArrayDeque<Reference>();
        queue.add(new Reference(root.get("id").getAsString(), true, kind));
        var seen = new HashSet<String>();
        while (!queue.isEmpty()) {
            var reference = queue.removeFirst();
            if (!seen.add(reference.pinned() + ":" + reference.id())) continue;
            if (seen.size() > 40) throw new IOException("Zu viele Abhängigkeiten (maximal 40)");
            JsonObject version;
            if (reference.pinned()) {
                version = reference.id().equals(root.get("id").getAsString()) ? root.deepCopy() : catalog.version(AddonRules.reference(reference.id())).deepCopy();
            } else {
                var candidates = ordered(catalog.versions(AddonRules.reference(reference.id()), reference.kind()));
                if (candidates.isEmpty()) throw new Rejected("Keine kompatible Minecraft-26.2-Version gefunden: " + reference.id());
                version = candidates.getFirst().deepCopy();
            }
            compatible(version, reference.kind());
            String project = version.get("project_id").getAsString();
            var previous = plan.putIfAbsent(project, version);
            if (previous != null && !previous.get("id").equals(version.get("id"))) throw new Rejected("Abhängigkeiten verlangen unterschiedliche Versionen desselben Projekts");
            if (previous != null) continue;
            version.addProperty("votex_kind", reference.kind());
            for (var raw : version.getAsJsonArray("dependencies")) {
                var dependency = raw.getAsJsonObject();
                if (!text(dependency, "dependency_type").equals("required")) continue;
                if (!text(dependency, "version_id").isEmpty()) queue.add(new Reference(text(dependency, "version_id"), true, "mod"));
                else if (!text(dependency, "project_id").isEmpty()) queue.add(new Reference(text(dependency, "project_id"), false, "mod"));
                else throw new IOException("Eine erforderliche Abhängigkeit kann nicht automatisch aufgelöst werden");
            }
        }
        for (var version : plan.values()) for (var raw : version.getAsJsonArray("dependencies")) {
            var dependency = raw.getAsJsonObject();
            if (!text(dependency, "dependency_type").equals("incompatible")) continue;
            for (var other : plan.values()) if (references(dependency, other)) throw new Rejected("Die Erweiterungen sind laut Modrinth inkompatibel");
            for (var rawOld : installed) {
                var old = rawOld.getAsJsonObject();
                if (!plan.containsKey(text(old, "project_id")) && references(dependency, old)) throw new Rejected("Inkompatibel mit einer bereits installierten Erweiterung");
            }
        }
        return plan;
    }

    private static List<JsonObject> ordered(JsonArray versions) {
        var stable = new ArrayList<JsonObject>();
        var alpha = new ArrayList<JsonObject>();
        for (var raw : versions) {
            var version = raw.getAsJsonObject();
            (text(version, "version_type").equals("alpha") ? alpha : stable).add(version);
        }
        stable.addAll(alpha);
        return stable;
    }

    private static void compatible(JsonObject version, String kind) throws Rejected {
        boolean game = false, loader = !kind.equals("mod") && !kind.equals("shader");
        for (var value : version.getAsJsonArray("game_versions")) game |= value.getAsString().equals("26.2");
        for (var value : version.getAsJsonArray("loaders")) loader |= value.getAsString().equals(kind.equals("shader") ? "iris" : "fabric");
        if (!game || !loader) throw new Rejected("Eine Abhängigkeit unterstützt Minecraft 26.2 / Fabric nicht");
    }

    private static boolean references(JsonObject dependency, JsonObject version) {
        return (!text(dependency, "version_id").isEmpty() && text(dependency, "version_id").equals(text(version, "id")))
            || (!text(dependency, "project_id").isEmpty() && text(dependency, "project_id").equals(text(version, "project_id")));
    }

    private static String text(JsonObject object, String key) {
        var value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}

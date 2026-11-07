package dev.votex.addons;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

class ModrinthGraphTest {
    private static JsonObject version(String project, String id, String... dependencies) {
        var value = new JsonObject();
        value.addProperty("id", id); value.addProperty("project_id", project); value.addProperty("version_type", "release");
        value.add("game_versions", JsonParser.parseString("[\"26.2\"]"));
        value.add("loaders", JsonParser.parseString("[\"fabric\"]"));
        var deps = new JsonArray();
        for (String dependency : dependencies) {
            var dep = new JsonObject(); dep.addProperty("project_id", dependency); dep.addProperty("dependency_type", "required"); deps.add(dep);
        }
        value.add("dependencies", deps); return value;
    }
    private static JsonArray array(JsonObject... values) { var result = new JsonArray(); for (var v : values) result.add(v); return result; }
    private static class Catalog implements ModrinthGraph.Catalog {
        final Map<String, JsonArray> projects = new HashMap<>();
        final Map<String, JsonObject> pins = new HashMap<>();
        public JsonArray versions(String id, String kind) throws Exception { return projects.getOrDefault(id, new JsonArray()); }
        public JsonObject version(String id) throws Exception { if (!pins.containsKey(id)) throw new IOException("HTTP 404 " + id); return pins.get(id); }
    }

    @Test void incompatibleRequiredGraphFallsBackWithoutLeakingFailedBranchOrMutatingMetadata() throws Exception {
        var catalog = new Catalog();
        var newest = version("zoom", "newest", "fabricKotlin", "forgeKotlin");
        var older = version("zoom", "older", "fabricKotlin");
        catalog.projects.put("zoom", array(newest, older));
        catalog.projects.put("fabricKotlin", array(version("fabricKotlin", "kotlin")));
        String unchanged = catalog.projects.get("zoom").toString();
        var plan = ModrinthGraph.resolve("zoom", "mod", new JsonArray(), catalog);
        assertEquals(Set.of("zoom", "fabricKotlin"), plan.keySet());
        assertEquals("older", plan.get("zoom").get("id").getAsString());
        assertEquals(unchanged, catalog.projects.get("zoom").toString());
    }

    @Test void exactDependencyPinsAreNeverReplacedByAConvenientCompatibleVersion() {
        var catalog = new Catalog(); var root = version("root", "r");
        root.getAsJsonArray("dependencies").add(JsonParser.parseString("{\"version_id\":\"locked\",\"project_id\":\"dep\",\"dependency_type\":\"required\"}"));
        var locked = version("dep", "locked"); locked.add("loaders", JsonParser.parseString("[\"neoforge\"]"));
        catalog.projects.put("root", array(root)); catalog.projects.put("dep", array(version("dep", "convenient"))); catalog.pins.put("locked", locked);
        assertThrows(ModrinthGraph.Rejected.class, () -> ModrinthGraph.resolve("root", "mod", new JsonArray(), catalog));
    }

    @Test void networkAndMalformedMetadataAbortBeforeTryingOlderReleases() {
        var catalog = new Catalog() {
            @Override public JsonArray versions(String id, String kind) throws Exception {
                if (id.equals("unreachable")) throw new IOException("HTTP 429");
                return super.versions(id, kind);
            }
        };
        catalog.projects.put("root", array(version("root", "new", "unreachable"), version("root", "old")));
        assertEquals("HTTP 429", assertThrows(IOException.class, () -> ModrinthGraph.resolve("root", "mod", new JsonArray(), catalog)).getMessage());
        var broken = version("root", "broken"); broken.remove("dependencies");
        catalog.projects.put("root", array(broken, version("root", "old")));
        assertThrows(NullPointerException.class, () -> ModrinthGraph.resolve("root", "mod", new JsonArray(), catalog));
    }

    @Test void incompatibleInstalledModCanSelectOlderRootButRequiredDependenciesCannotBeSkipped() throws Exception {
        var catalog = new Catalog(); var bad = version("root", "new");
        bad.getAsJsonArray("dependencies").add(JsonParser.parseString("{\"project_id\":\"kept\",\"dependency_type\":\"incompatible\"}"));
        catalog.projects.put("root", array(bad, version("root", "old", "needed")));
        var installed = array(version("kept", "existing"));
        assertThrows(ModrinthGraph.Rejected.class, () -> ModrinthGraph.resolve("root", "mod", installed, catalog));
        catalog.projects.put("needed", array(version("needed", "dependency")));
        assertEquals("old", ModrinthGraph.resolve("root", "mod", installed, catalog).get("root").get("id").getAsString());
    }

    @Test void rootFallbackIsBoundedToSixteenReleases() {
        var catalog = new Catalog(); var candidates = new JsonArray();
        for (int i = 0; i < 16; i++) candidates.add(version("root", "bad" + i, "missing"));
        candidates.add(version("root", "outsideBound")); catalog.projects.put("root", candidates);
        assertThrows(ModrinthGraph.Rejected.class, () -> ModrinthGraph.resolve("root", "mod", new JsonArray(), catalog));
    }

    @Test @EnabledIfEnvironmentVariable(named="VOTEX_GRAPH_HTTPS_TEST", matches="1")
    void realZoomifyMetadataUsesCompleteFabricGraphWithoutForgeKotlin() throws Exception {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        var catalog = new ModrinthGraph.Catalog() {
            JsonElement get(String path) throws Exception {
                var response = http.send(HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2" + path)).timeout(Duration.ofSeconds(30)).header("User-Agent", "VotexClient-GraphQA/0.4.1").build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                return JsonParser.parseString(response.body());
            }
            public JsonArray versions(String id, String kind) throws Exception { return get("/project/" + AddonRules.reference(id) + "/version?game_versions=%5B%2226.2%22%5D&loaders=%5B%22fabric%22%5D").getAsJsonArray(); }
            public JsonObject version(String id) throws Exception { return get("/version/" + AddonRules.reference(id)).getAsJsonObject(); }
        };
        var plan = ModrinthGraph.resolve("zoomify", "mod", new JsonArray(), catalog);
        assertEquals("2Qr8jSFc", plan.get("w7ThoJFB").get("id").getAsString());
        assertTrue(plan.keySet().containsAll(Set.of("Ha28R6CL", "1eAoo2KR", "P7dR8mSH")));
        assertFalse(plan.containsKey("1vrSzlao"));
        System.out.println("VOTEX_JAVA_GRAPH_HTTPS_PASS selected=2Qr8jSFc requiredFabricDependencies=true forgeKotlin=false downloads=false game=false");
    }
}

package dev.votex.cosmetics;

/** Bounded, deterministic companion gestures; no per-frame allocations. */
public final class CompanionMotion {
    private static final com.google.gson.JsonObject PROFILES = profiles();
    private static com.google.gson.JsonObject profiles() {
        try (var input = CompanionMotion.class.getResourceAsStream("/assets/votex_client/companion-profiles.json")) {
            if (input == null) throw new IllegalStateException("Missing companion profiles");
            return com.google.gson.JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new IllegalStateException("Invalid companion profiles", e); }
    }
    private CompanionMotion() {}
    public static boolean supports(String id) {
        return PROFILES.has(id);
    }
    public static float lift(String id, float seconds, float phase, float velocity) {
        var profile = PROFILES.getAsJsonObject(id);
        if (profile == null) return 0;
        String kind = profile.get("kind").getAsString();
        if (!kind.equals("organic")) return (float) (Math.sin(seconds * (kind.equals("mechanical") ? 1.1 : 1.7) + phase) * .25 * profile.get("liftScale").getAsFloat());
        return lift(seconds, phase, velocity) * profile.get("liftScale").getAsFloat();
    }
    public static float tilt(String id, float seconds, float phase) {
        var profile = PROFILES.getAsJsonObject(id);
        if (profile == null) return 0;
        return (profile.get("kind").getAsString().equals("organic") ? tilt(seconds, phase) : (float) Math.sin(seconds * 1.2 + phase) * .035f) * profile.get("tiltScale").getAsFloat();
    }
    /** A short outward leap returns to the same shoulder without world entities. */
    public static float outward(String id, float seconds, float phase) {
        var profile = PROFILES.getAsJsonObject(id);
        if (profile == null || !profile.get("kind").getAsString().equals("organic")) return 0;
        double cycle = (seconds + phase) % 17;
        if (cycle <= 12 || cycle >= 13) return 0;
        return (float) (-Math.pow(Math.sin((cycle - 12) * Math.PI), 2) * 2.5);
    }
    public static float lift(float seconds, float phase, float velocity) {
        double cycle = (seconds + phase) % 17;
        double hop = cycle > 12 && cycle < 13 ? -Math.pow(Math.sin((cycle - 12) * Math.PI), 2) * 1.35 : 0;
        return (float) (Math.sin(seconds * 1.8 + phase) * .08 + hop + Math.clamp(velocity * -.08, -.25, .25));
    }
    public static float tilt(float seconds, float phase) {
        double cycle = (seconds + phase) % 17;
        double shake = cycle > 5 && cycle < 6.2 ? Math.sin((cycle - 5) * Math.PI / 1.2) * Math.sin((cycle - 5) * 32) * .085 : 0;
        return (float) (Math.sin(seconds * 1.2 + phase) * .012 + shake);
    }
}

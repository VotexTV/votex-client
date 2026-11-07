package dev.votex.client;

import com.google.gson.*;
import dev.votex.cosmetics.CompanionMotion;
import dev.votex.cosmetics.CompanionFit;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Indexed authored surfaces, batched on look changes and animated with bounded per-player state. */
public final class WearableLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("votex_client", "textures/white.png");
    private static final Set<String> JOINTS = Set.of("head", "body", "leftArm", "rightArm", "leftLeg", "rightLeg");
    private static final Map<String, JsonObject> MODELS = new LinkedHashMap<>(16, .75f, true);
    private static final Map<String, JsonObject> CUSTOM_MODELS = new HashMap<>();
    private static final Map<UUID, Map<String, JsonObject>> REMOTE_MODELS = new HashMap<>();
    private static final Set<WearableLayer> INSTANCES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<AvatarRenderState, Kinematics> RENDER_KINEMATICS = Collections.synchronizedMap(new WeakHashMap<>());

    private record Key(float time, float angle) {}
    private record Motion(String type, int side, String axis, float amplitude, float speed, float phase,
                         float x, float y, float z, float duration, float secondary,
                         float secondaryX, float secondaryY, float secondaryZ, String secondaryAxis, List<Key> keys) {}
    private record Vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v, int color) {}
    private record Kinematics(double horizontalSpeed, double verticalSpeed, boolean onGround, boolean sprinting, boolean gliding, float yaw, double distanceSquared) {}
    private static final Kinematics STILL = new Kinematics(0, 0, true, false, false, 0, 0);
    private static volatile Kinematics TEST_KINEMATICS;

    private static final class Piece {
        final String joint;
        final Motion motion;
        final boolean wings;
        final boolean transparent;
        final Identifier texture;
        final List<Vertex> vertices;
        final String companion;
        final CompanionFit fit;
        Piece(String joint, Motion motion, boolean transparent, Identifier texture, List<Vertex> vertices, String companion, CompanionFit fit, boolean wings) {
            this.joint = joint; this.motion = motion; this.transparent = transparent; this.texture = texture; this.vertices = vertices;
            this.companion = companion;
            this.fit = fit; this.wings = wings;
        }
    }
    private record Baked(List<Piece> pieces, List<ModelPart> cape, int revision) {}
    private record Shape(List<Piece> pieces, List<ModelPart> cape) {}
    private static final Map<String, Shape> SHAPES = new LinkedHashMap<>(16, .75f, true);
    private static long shapeHits;
    static long sharedShapeHits() { return shapeHits; }
    private static String sharedShapeKey(JsonObject look) {
        if (!look.has("equipment") || !look.get("equipment").isJsonObject()) return null;
        var entries = new TreeMap<String, String>();
        for (var entry : look.getAsJsonObject("equipment").entrySet()) {
            if (entry.getKey().equals("cape")) continue;
            if (!entry.getValue().isJsonPrimitive()) return null;
            String id = entry.getValue().getAsString();
            if (id.startsWith("custom-") || !id.matches("[a-z0-9-]{1,60}") || !entry.getKey().matches("[a-z]{1,12}")) return null;
            entries.put(entry.getKey(), id);
        }
        return entries.toString();
    }

    private static final class Dynamics {
        long lastNanos;
        boolean grounded;
        boolean initialized;
        float wing, wingVelocity, cape, capeVelocity, impulse, companionTilt, companionVelocity, lastYaw, capeRoll, capeRollVelocity;
        void update(long now, double horizontalSpeed, double verticalSpeed, boolean onGround,
                    boolean sprinting, boolean gliding, float yaw, float gait, float age, int tier) {
            long minInterval = tier == 0 ? 0 : tier == 1 ? 16_000_000L : 33_000_000L;
            if (lastNanos != 0 && now - lastNanos < minInterval) return;
            float dt = lastNanos == 0 ? 1f / 60f : (float) Math.clamp((now - lastNanos) / 1_000_000_000d, .001, .25);
            lastNanos = now;
            float turn = initialized ? (float) Math.IEEEremainder(yaw - lastYaw, 360) / dt : 0;
            lastYaw = yaw;
            double speed = Math.clamp(horizontalSpeed, 0, .42);
            float movement = Math.max((float) Math.clamp(speed / .16, 0, 1), (float) Math.clamp(gait * 3, 0, 1));
            if (initialized && grounded && !onGround && verticalSpeed > .06) impulse = .32f;
            else if (initialized && !grounded && onGround) impulse = -.23f;
            grounded = onGround;
            float cycle = (float) Math.sin(age * (.11f + movement * .09f));
            float wingGoal = .018f + movement * (sprinting ? .13f : .07f) * cycle + (sprinting ? .055f : 0);
            if (!onGround && !gliding) wingGoal += verticalSpeed > .03 ? .22f : .20f + (float) Math.clamp(-verticalSpeed * .045, 0, .25);
            if (gliding) wingGoal = .025f + cycle * .045f;
            wingGoal += impulse;
            impulse *= (float) Math.exp(-dt / .16f);
            wing = spring(wing, this, true, wingGoal, dt);
            wing = (float) Math.clamp(wing, -.25, .68);

            float capeGoal = 8f + movement * 11f + (sprinting ? 3f : 0f);
            if (!onGround && !gliding) capeGoal += verticalSpeed > .03 ? -4f : (float) Math.clamp(-verticalSpeed * 5, 0, 20);
            if (gliding) capeGoal = 48f;
            cape = spring(cape, this, false, capeGoal, dt);
            cape = (float) Math.clamp(cape, 2, 58);
            // A separate spring lets a shoulder companion settle after a jump
            // without borrowing the wing angle or snapping to the new speed.
            float companionGoal = (float) Math.clamp(-verticalSpeed * .10 + (sprinting ? .025 : 0), -.09, .09);
            int companionSteps = Math.max(1, (int) Math.ceil(dt / .008));
            float step = dt / companionSteps;
            for (int i = 0; i < companionSteps; i++) {
                companionVelocity += ((companionGoal - companionTilt) * 121 - companionVelocity * 17.6f) * step;
                companionTilt += companionVelocity * step;
                float rollGoal = (float) Math.clamp(-turn * .018f, -10, 10);
                capeRollVelocity += ((rollGoal - capeRoll) * 81 - capeRollVelocity * 15.3f) * step;
                capeRoll += capeRollVelocity * step;
            }
            initialized = true;
        }
    }
    private static float spring(float value, Dynamics state, boolean wing, float target, float dt) {
        float velocity = wing ? state.wingVelocity : state.capeVelocity;
        int steps = Math.max(1, (int) Math.ceil(dt / .008));
        float step = dt / steps;
        for (int i = 0; i < steps; i++) {
            float omega = wing ? 13.5f : 9.5f;
            float acceleration = (target - value) * omega * omega - velocity * omega * 1.72f;
            velocity += acceleration * step;
            value += velocity * step;
        }
        if (wing) state.wingVelocity = velocity; else state.capeVelocity = velocity;
        return value;
    }

    private static final Map<UUID, Dynamics> DYNAMICS = new LinkedHashMap<>(16, .75f, true);
    private final Map<UUID, Baked> baked = new LinkedHashMap<>(16, .75f, true);

    public WearableLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); INSTANCES.add(this); }

    /** Called once when vanilla extracts each player's render state; retains no entity or world references. */
    public static void captureKinematics(Avatar player, AvatarRenderState state) {
        var velocity = player.getDeltaMovement();
        var localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        double distanceSquared = localPlayer == null ? 0 : localPlayer.distanceToSqr(player);
        Kinematics test = TEST_KINEMATICS;
        RENDER_KINEMATICS.put(state, test == null ? new Kinematics(Math.sqrt(velocity.horizontalDistanceSqr()), velocity.y, player.onGround(), player.isSprinting(), player.isFallFlying(), state.bodyRot, distanceSquared) : test);
    }
    static void testKinematics(double horizontal, double vertical, boolean onGround, boolean sprinting, boolean gliding) {
        if (!"1".equals(System.getenv("VOTEX_COSMETICS2_TEST")) || !net.minecraft.client.Minecraft.getInstance().gameDirectory.toString().toLowerCase(Locale.ROOT).contains("clientgametest")) throw new IllegalStateException("Motion overrides are limited to the isolated Cosmetics 2 game test");
        TEST_KINEMATICS = new Kinematics(horizontal, vertical, onGround, sprinting, gliding, 0, 0);
    }
    static void clearTestKinematics() { TEST_KINEMATICS = null; }
    public static void remoteModels(UUID player, JsonArray values) {
        if (REMOTE_MODELS.size() >= 128 && !REMOTE_MODELS.containsKey(player)) return;
        Map<String, JsonObject> models = new HashMap<>();
        for (var item : values) { JsonObject model = item.getAsJsonObject(); models.put(model.get("id").getAsString(), model); }
        REMOTE_MODELS.put(player, Map.copyOf(models));
    }
    public static void removeRemote(UUID player) {
        REMOTE_MODELS.remove(player); DYNAMICS.remove(player);
        for (var layer : INSTANCES) layer.baked.remove(player);
    }
    private static JsonObject model(UUID owner, String id) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && !owner.equals(player.getUUID()) && id.startsWith("custom-")) return REMOTE_MODELS.getOrDefault(owner, Map.of()).get(id);
        return model(id);
    }
    static void setCustomModels(JsonArray models) {
        CUSTOM_MODELS.clear(); if (models == null || models.size() > 50) return;
        for (JsonElement element : models) try {
            JsonObject model = element.getAsJsonObject(); String id = model.get("id").getAsString();
            if (id.matches("custom-[a-f0-9-]{1,40}") && model.get("schemaVersion").getAsInt() == 2 && model.getAsJsonArray("parts").size() <= 24) CUSTOM_MODELS.put(id, model);
        } catch (Exception ignored) {}
    }
    static JsonObject model(String id) {
        if (!id.matches("[a-z0-9-]{1,60}")) return null;
        if (CUSTOM_MODELS.containsKey(id)) return CUSTOM_MODELS.get(id);
        if (MODELS.containsKey(id)) return MODELS.get(id);
        try (var stream = WearableLayer.class.getResourceAsStream("/assets/votex_client/catalog/" + id + ".json")) {
            if (stream == null) return null;
            byte[] bytes = stream.readNBytes(2_000_001); if (bytes.length > 2_000_000) return null;
            JsonObject m = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!m.get("id").getAsString().equals(id) || m.get("schemaVersion").getAsInt() != 2 || m.getAsJsonArray("parts").size() > 128) return null;
            if (MODELS.size() >= 16) MODELS.remove(MODELS.keySet().iterator().next()); MODELS.put(id, m); return m;
        } catch (Exception e) { return null; }
    }
    private static float value(JsonArray a, int i, float bound) { float v = a.get(i).getAsFloat(); if (!Float.isFinite(v) || Math.abs(v) > bound) throw new IllegalArgumentException("Invalid mesh value"); return v; }
    private static float bounded(JsonObject object, String key, float fallback, float min, float max) {
        float v = object.has(key) ? object.get(key).getAsFloat() : fallback;
        if (!Float.isFinite(v) || v < min || v > max) throw new IllegalArgumentException("Invalid motion parameter: " + key);
        return v;
    }
    private static Motion parseMotion(JsonObject mm) {
        JsonArray pivot = mm.getAsJsonArray("pivot"); String type = mm.has("type") ? mm.get("type").getAsString() : (mm.has("side") ? "wing" : "oscillate");
        String axis = mm.has("axis") ? mm.get("axis").getAsString() : (type.equals("wing") ? "z" : "y");
        if (!Set.of("wing", "oscillate").contains(type) || !Set.of("x", "y", "z").contains(axis)) throw new IllegalArgumentException("Invalid motion type");
        float amplitude = bounded(mm, "amplitude", .045f, 0, 1.2f), speed = bounded(mm, "speed", 1.35f, .1f, 8), phase = bounded(mm, "phase", 0, -6.3f, 6.3f), duration = bounded(mm, "duration", 2, 1, 10);
        float secondary = bounded(mm, "secondary", 0, 0, .8f), sx = 0, sy = 0, sz = 0;
        String secondaryAxis = mm.has("secondaryAxis") ? mm.get("secondaryAxis").getAsString() : axis;
        if (!Set.of("x", "y", "z").contains(secondaryAxis)) throw new IllegalArgumentException("Invalid secondary axis");
        if (secondary > 0) { JsonArray second = mm.getAsJsonArray("secondaryPivot"); if (second == null || second.size() != 3) throw new IllegalArgumentException("Invalid secondary pivot"); sx = value(second, 0, 100) / 16; sy = value(second, 1, 100) / 16; sz = value(second, 2, 100) / 16; }
        List<Key> keys = new ArrayList<>();
        if (mm.has("keyframes")) {
            JsonArray frames = mm.getAsJsonArray("keyframes"); if (frames.size() < 2 || frames.size() > 24) throw new IllegalArgumentException("Invalid keyframes"); float previous = -1;
            for (var element : frames) { JsonObject frame = element.getAsJsonObject(); float time = frame.get("time").getAsFloat(), angle = frame.get("angle").getAsFloat(); if (!Float.isFinite(time) || time < 0 || time > 1 || time <= previous || !Float.isFinite(angle) || Math.abs(angle) > 3) throw new IllegalArgumentException("Invalid keyframe"); keys.add(new Key(time, angle)); previous = time; }
            if (keys.getFirst().time != 0 || keys.getLast().time != 1) throw new IllegalArgumentException("Incomplete timeline");
        }
        return new Motion(type, mm.has("side") ? mm.get("side").getAsInt() : 0, axis, amplitude, speed, phase,
                value(pivot, 0, 100) / 16, value(pivot, 1, 100) / 16, value(pivot, 2, 100) / 16,
                duration, secondary, sx, sy, sz, secondaryAxis, List.copyOf(keys));
    }
    private static float sample(Motion motion, float seconds) {
        if (motion.keys.isEmpty()) return (float) Math.sin(seconds * motion.speed + motion.phase) * motion.amplitude;
        float time = (seconds / motion.duration) % 1;
        for (int i = 1; i < motion.keys.size(); i++) { Key b = motion.keys.get(i), a = motion.keys.get(i - 1); if (time <= b.time) { float t = (time - a.time) / (b.time - a.time), ease = t * t * (3 - 2 * t); return a.angle + (b.angle - a.angle) * ease; } }
        return motion.keys.getLast().angle;
    }

    private Baked build(JsonObject look, UUID owner, int revision) {
        String sharedKey = sharedShapeKey(look);
        Shape shared = sharedKey == null ? null : SHAPES.get(sharedKey);
        if (shared != null) { shapeHits++; return new Baked(shared.pieces, shared.cape, revision); }
        CompanionFit companionFit = fit(look, owner);
        Map<String, Piece> groups = new LinkedHashMap<>(); int total = 0;
        if (look.has("equipment") && look.get("equipment").isJsonObject()) for (var entry : look.getAsJsonObject("equipment").entrySet()) {
            if (entry.getKey().equals("cape")) continue;
            JsonObject model = model(owner, entry.getValue().getAsString()); if (model == null || !model.get("slot").getAsString().equals(entry.getKey())) continue;
            for (JsonElement element : model.getAsJsonArray("parts")) try {
                JsonObject p = element.getAsJsonObject(); String joint = p.get("joint").getAsString(); if (!JOINTS.contains(joint)) continue;
                JsonArray positions = p.getAsJsonArray("positions"), normals = p.getAsJsonArray("normals"), colors = p.getAsJsonArray("colors"), quads = p.getAsJsonArray("quads");
                if (positions.size() % 3 != 0 || positions.size() > 60_000 || normals.size() != positions.size() || colors.size() != positions.size() || quads.size() % 4 != 0 || quads.size() > 24_000) throw new IllegalArgumentException("Invalid mesh layout");
                total += quads.size(); if (total > 100_000) throw new IllegalArgumentException("Look exceeds mesh budget");
                float opacity = p.getAsJsonObject("material").get("opacity").getAsFloat(); if (!Float.isFinite(opacity) || opacity < 0 || opacity > 1) throw new IllegalArgumentException("Invalid opacity");
                Identifier texture = WHITE; JsonArray uv = null;
                if (p.has("texture")) { String name = p.get("texture").getAsString(); if (!name.matches("[a-z0-9-]{1,70}\\.png")) throw new IllegalArgumentException("Invalid texture name"); uv = p.getAsJsonArray("uvs"); if (uv == null || uv.size() != positions.size() / 3 * 2) throw new IllegalArgumentException("Invalid texture coordinates"); texture = Identifier.fromNamespaceAndPath("votex_client", "textures/cosmetics/" + name); }
                Motion motion = p.has("motion") ? parseMotion(p.getAsJsonObject("motion")) : null;
                String id = model.get("id").getAsString();
                String companion = entry.getKey().equals("pet") && CompanionMotion.supports(id) ? id : null;
                boolean wings = entry.getKey().equals("wings");
                String key = wings + "/" + joint + "/" + motion + "/" + (opacity < 1) + "/" + texture + "/" + companion; Piece piece = groups.get(key);
                if (piece == null) { piece = new Piece(joint, motion, opacity < 1, texture, new ArrayList<>(), companion, companion == null ? CompanionFit.IDENTITY : companionFit, wings); groups.put(key, piece); }
                for (JsonElement q : quads) {
                    int index = q.getAsInt(), i = index * 3; if (index < 0 || i + 2 >= positions.size()) throw new IllegalArgumentException("Mesh index out of bounds");
                    int r = (int) (Math.max(0, Math.min(1, value(colors, i, 1))) * 255), g = (int) (Math.max(0, Math.min(1, value(colors, i + 1, 1))) * 255), b = (int) (Math.max(0, Math.min(1, value(colors, i + 2, 1))) * 255), a = (int) (opacity * 255);
                    piece.vertices.add(new Vertex(value(positions, i, 100) / 16, value(positions, i + 1, 100) / 16, value(positions, i + 2, 100) / 16,
                            value(normals, i, 1.01f), value(normals, i + 1, 1.01f), value(normals, i + 2, 1.01f), uv == null ? .5f : value(uv, index * 2, 16), uv == null ? .5f : value(uv, index * 2 + 1, 16), (a << 24) | (r << 16) | (g << 8) | b));
                }
            } catch (Exception e) { if (VotexClient.INSTANCE != null) VotexClient.INSTANCE.error("Kosmetikfläche konnte nicht geladen werden", e); }
        }
        // Four connected volumes share the original cape atlas. Each segment has
        // one front and one back face; there is no second overlapping cape mesh.
        List<ModelPart> strips = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("strip", CubeListBuilder.create().texOffs(0, i * 4).addBox(-5, 0, 0, 10, 4, 1), PartPose.ZERO);
            strips.add(LayerDefinition.create(mesh, 64, 32).bakeRoot());
        }
        Shape shape = new Shape(List.copyOf(groups.values()), List.copyOf(strips));
        if (sharedKey != null) {
            SHAPES.put(sharedKey, shape);
            while (SHAPES.size() > 16) SHAPES.remove(SHAPES.keySet().iterator().next());
        }
        return new Baked(shape.pieces, shape.cape, revision);
    }

    private static CompanionFit fit(JsonObject look, UUID owner) {
        try {
            var equipment = look.getAsJsonObject("equipment");
            if (equipment == null || !equipment.has("pet")) return CompanionFit.IDENTITY;
            String id = equipment.get("pet").getAsString();
            if (!CompanionMotion.supports(id)) return CompanionFit.IDENTITY;
            JsonObject pet = model(owner, id); if (pet == null) return CompanionFit.IDENTITY;
            float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
            for(var part:pet.getAsJsonArray("parts")){var positions=part.getAsJsonObject().getAsJsonArray("positions");for(int i=0;i+2<positions.size();i+=3){float x=positions.get(i).getAsFloat();minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,positions.get(i+1).getAsFloat());maxY=Math.max(maxY,positions.get(i+1).getAsFloat());}}
            String head=equipment.has("head")?equipment.get("head").getAsString():"";
            return CompanionFit.of(minX,maxX,minY,maxY,head.equals("creator-headphones")||head.equals("orbit-headphones"));
        } catch(RuntimeException invalid) { return CompanionFit.IDENTITY; }
    }

    @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        JsonObject look = LocalLooks.forState(state); UUID owner = LocalLooks.playerId(state); if (look == null || owner == null || state.isSpectator) return;
        long timer = VotexPerformanceEngine.INSTANCE.timer(); int revision = LocalLooks.revision(state); Baked appearance = baked.get(owner);
        if (appearance == null || appearance.revision != revision) { appearance = build(look, owner, revision); baked.put(owner, appearance); while (baked.size() > 128) baked.remove(baked.keySet().iterator().next()); }
        PlayerModel m = getParentModel(); boolean animate = VotexClient.INSTANCE != null && VotexClient.INSTANCE.theme.wobble() > 0;
        float lookScale = LocalLooks.scale(state); Kinematics k = RENDER_KINEMATICS.getOrDefault(state, STILL); long now = System.nanoTime();
        float detail = (float) Math.clamp((64 - Math.sqrt(k.distanceSquared)) / 24, 0, 1);
        int motionTier = Math.max(VotexPerformanceEngine.INSTANCE.adaptiveTier(), k.distanceSquared > 32 * 32 ? 2 : k.distanceSquared > 16 * 16 ? 1 : 0);
        Dynamics motionState = DYNAMICS.computeIfAbsent(owner, ignored -> new Dynamics());
        motionState.update(now, k.horizontalSpeed, k.verticalSpeed, k.onGround, k.sprinting, k.gliding, k.yaw, state.walkAnimationSpeed, state.ageInTicks, motionTier);
        while (DYNAMICS.size() > 128) DYNAMICS.remove(DYNAMICS.keySet().iterator().next());
        if (!animate) { motionState.wing = 0; motionState.wingVelocity = 0; motionState.impulse = 0; motionState.cape = 8; motionState.capeVelocity = 0; motionState.companionTilt = 0; motionState.companionVelocity = 0; motionState.capeRoll = 0; motionState.capeRollVelocity = 0; }
        float seconds = state.ageInTicks * .05f;
        float stillness = k.onGround && !k.gliding
                ? 1f - (float) Math.clamp(Math.max(k.horizontalSpeed / .09, state.walkAnimationSpeed * 3), 0, 1) : 0;
        float idleSweep = animate ? (float) Math.sin(seconds * 2.3f) * .27f * stillness * detail : 0;
        if (LocalLooks.visible(state)) for (Piece p : appearance.pieces) {
            ModelPart joint = switch (p.joint) { case "head" -> m.head; case "leftArm" -> m.leftArm; case "rightArm" -> m.rightArm; case "leftLeg" -> m.leftLeg; case "rightLeg" -> m.rightLeg; default -> m.body; };
            poses.pushPose(); joint.translateAndRotate(poses);
            if (p.wings) poses.translate(0, 0, (1 - lookScale) * 2f / 16);
            poses.scale(lookScale, lookScale, lookScale);
            if (p.companion != null) {
                poses.translate(p.fit.x()/16,p.fit.y()/16,0);poses.translate(-6f/16,0,0);
                poses.scale(p.fit.scale(),p.fit.scale(),p.fit.scale());poses.translate(6f/16,0,0);
            }
            if (p.companion != null && animate) {
                float phase = (owner.hashCode() & 255) * .023f;
                poses.translate(-6f / 16, 0, 0);
                poses.translate(CompanionMotion.outward(p.companion, seconds, phase) / 16 * detail, 0, 0);
                poses.translate(0, CompanionMotion.lift(p.companion, seconds, phase, motionState.companionVelocity) / 16 * detail, 0);
                poses.mulPose(com.mojang.math.Axis.XP.rotation(motionState.companionTilt));
                poses.mulPose(com.mojang.math.Axis.ZP.rotation(CompanionMotion.tilt(p.companion, seconds, phase) * detail));
                poses.translate(6f / 16, 0, 0);
            }
            if (p.motion != null) {
                Motion mm = p.motion; poses.translate(mm.x, mm.y, mm.z);
                float angle = mm.type.equals("wing") ? (motionState.wing + (animate ? sample(mm, state.ageInTicks * .05f) * .25f * detail : 0)) * mm.side : animate ? sample(mm, state.ageInTicks * .05f) * detail : 0;
                if (mm.type.equals("wing") && idleSweep != 0) poses.mulPose(rotation("y", idleSweep * mm.side));
                poses.mulPose(rotation(mm.axis, angle)); poses.translate(-mm.x, -mm.y, -mm.z);
                if (mm.secondary > 0 && animate) {
                    float secondary = (angle * .35f + (float) Math.sin(seconds * 2.3f + mm.phase - .75f) * mm.secondary * (.08f + .13f * stillness) * mm.side) * detail;
                    poses.translate(mm.secondaryX, mm.secondaryY, mm.secondaryZ); poses.mulPose(rotation(mm.secondaryAxis, secondary)); poses.translate(-mm.secondaryX, -mm.secondaryY, -mm.secondaryZ);
                }
            }
            collector.submitCustomGeometry(poses, p.transparent ? RenderTypes.entityTranslucent(p.texture) : RenderTypes.entityCutout(p.texture), (pose, consumer) -> {
                for (Vertex v : p.vertices) consumer.addVertex(pose, v.x, v.y, v.z).setColor(v.color).setUv(v.u, v.v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, v.nx, v.ny, v.nz);
            });
            poses.popPose();
        }
        if (LocalLooks.capeVisible(state) && LocalLooks.enabled(state, "cape") && state.showCape && !state.isFallFlying) {
            poses.pushPose(); m.body.translateAndRotate(poses);
            // Rotate about the cape attachment, not the centre of the player's body.
            poses.translate(0, 0, 2.6f / 16f);
            poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(animate ? (float) Math.clamp(motionState.capeRoll, -12, 12) : 0));
            poses.mulPose(com.mojang.math.Axis.XP.rotationDegrees(motionState.cape));
            Identifier capeTexture = LocalLooks.cape(state);
            for (int i = 0; i < appearance.cape.size(); i++) {
                if (i > 0) {
                    poses.translate(0, 4f / 16, 0);
                    float bend = animate ? (float) Math.sin(state.ageInTicks * .095f - i * .55f) * 1.2f + motionState.capeVelocity * .012f * i : 0;
                    poses.mulPose(com.mojang.math.Axis.XP.rotationDegrees((float) Math.clamp(bend, -6, 8)));
                }
                collector.submitModelPart(appearance.cape.get(i), poses, RenderTypes.entityCutout(capeTexture), light, OverlayTexture.NO_OVERLAY, null);
            }
            poses.popPose();
        }
        VotexPerformanceEngine.INSTANCE.cosmetic(timer);
    }
    private static org.joml.Quaternionf rotation(String axis, float angle) {
        return switch (axis) { case "x" -> com.mojang.math.Axis.XP.rotation(angle); case "z" -> com.mojang.math.Axis.ZP.rotation(angle); default -> com.mojang.math.Axis.YP.rotation(angle); };
    }
}

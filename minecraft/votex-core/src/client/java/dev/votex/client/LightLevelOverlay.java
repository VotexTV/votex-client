package dev.votex.client;

import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.LightLayer;

/** A bounded, depth-tested floor overlay for the normal monster light rule. */
final class LightLevelOverlay {
    private static final int HEIGHT_RANGE = 5;
    private static final int GREEN = 0xff70f29e, YELLOW = 0xffffdb62, RED = 0xffff636d;
    private record Label(BlockPos position, int light, int color) {}
    private static final FormattedCharSequence[] NUMBERS = new FormattedCharSequence[16];
    static {
        for (int i = 0; i < NUMBERS.length; i++)
            NUMBERS[i] = Component.literal(Integer.toString(i)).getVisualOrderText();
    }

    private ClientLevel world;
    private List<Label> labels = List.of();
    private int ticksUntilRefresh;

    void tick(Minecraft mc, boolean enabled, int radius, boolean risksOnly) {
        if (!enabled || mc.player == null || mc.level == null) {
            labels = List.of();
            world = null;
            ticksUntilRefresh = 0;
            return;
        }
        if (world == mc.level && ticksUntilRefresh-- > 0) return;
        world = mc.level;
        ticksUntilRefresh = 7;
        BlockPos center = mc.player.blockPosition();
        List<Label> next = new ArrayList<>();
        var feet = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                int x = center.getX() + dx, z = center.getZ() + dz;
                if (!world.hasChunk(x >> 4, z >> 4)) continue;
                for (int step = 0; step <= HEIGHT_RANGE * 2; step++) {
                    int dy = (step + 1) / 2 * (step % 2 == 0 ? -1 : 1);
                    feet.set(x, center.getY() + dy, z);
                    var floor = feet.below();
                    var surface = world.getBlockState(floor);
                    if (!surface.isFaceSturdy(world, floor, Direction.UP)
                            || !world.getBlockState(feet).isAir()
                            || !world.getBlockState(feet.above()).isAir()) continue;
                    int block = world.getBrightness(LightLayer.BLOCK, feet);
                    int sky = world.getBrightness(LightLayer.SKY, feet);
                    int max = world.dimensionType().monsterSpawnLightTest().maxInclusive();
                    int blockLimit = world.dimensionType().monsterSpawnBlockLightLimit();
                    int current = world.getMaxLocalRawBrightness(feet, world.isThundering() ? 10 : world.getSkyDarken());
                    int atNight = Math.max(block, sky - 11);
                    boolean eligible = world.getDifficulty() != Difficulty.PEACEFUL
                            && surface.isValidSpawn(world, floor, EntityTypes.ZOMBIE) && block <= blockLimit;
                    int color = !eligible ? GREEN : current <= max ? RED
                            : world.dimensionType().hasSkyLight() && atNight <= max ? YELLOW : GREEN;
                    if (!risksOnly || color != GREEN) next.add(new Label(feet.immutable(), block, color));
                    break;
                }
            }
        }
        labels = next;
    }

    void render(LevelRenderContext context) {
        var mc = Minecraft.getInstance();
        if (labels.isEmpty() || mc.level != world || mc.gui.screen() != null || mc.gui.hud.isHidden()) return;
        var camera = context.levelState().cameraRenderState.pos;
        var pose = context.poseStack();
        var queue = context.submitNodeCollector().order(0);
        for (Label label : labels) {
            var p = label.position();
            pose.pushPose();
            pose.translate(p.getX() + .5 - camera.x, p.getY() + .035 - camera.y, p.getZ() + .5 - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180f-mc.player.getYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(.052f, .052f, .052f);
            var text = NUMBERS[label.light()];
            queue.submitText(pose, -mc.font.width(text) / 2f, -4, text, false,
                    Font.DisplayMode.NORMAL, 0x00f000f0, label.color(), 0, 0);
            pose.popPose();
        }
    }
}

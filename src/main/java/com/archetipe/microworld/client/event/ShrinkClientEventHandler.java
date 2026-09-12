package com.archetipe.microworld.client.event;

import com.archetipe.microworld.Microworld;
import com.archetipe.microworld.block.MiniatureBlock;
import com.archetipe.microworld.block.entity.MiniatureBlockEntity;
import com.archetipe.microworld.block.entity.MiniatureVoxel;
import com.archetipe.microworld.client.world.BlockPixelSampler;
import com.archetipe.microworld.item.ShrinkRayItem;
import com.archetipe.microworld.network.NetworkHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = Microworld.MODID, value = Dist.CLIENT)
public class ShrinkClientEventHandler {

    private static final float FOV_MULTIPLIER = 1.4f;
    private static final int SUB_GRID = 16;
    private static final int SCALE = 16;

    private static final float OUTLINE_R = 0.0f, OUTLINE_G = 0.0f;
    private static final float OUTLINE_B = 0.0f, OUTLINE_A = 0.4f;

    private static BlockPos digPos = null;
    private static BlockState digSource = null;
    private static int digSubX, digSubY, digSubZ;
    private static Direction digFace = null;
    private static float digProgress = 0f;
    private static float digHardness = 1.0f;

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!ShrinkRayItem.isShrunk(player)) return;
        event.setFOV(event.getFOV() * FOV_MULTIPLIER);
    }

    @SubscribeEvent
    public static void onRenderHighlight(RenderHighlightEvent.Block event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !ShrinkRayItem.isShrunk(player)) return;

        event.setCanceled(true);

        BlockHitResult hit = event.getTarget();
        BlockPos pos = hit.getBlockPos();
        Vec3 loc = hit.getLocation();
        Vec3 cam = event.getCamera().getPosition();

        Level level = Minecraft.getInstance().level;
        int grid = SUB_GRID;
        if (level != null && level.getBlockState(pos).getBlock() instanceof MiniatureBlock) {
            if (level.getBlockEntity(pos) instanceof MiniatureBlockEntity mini) {
                int ms = mini.getScale();
                if (ms > 0) grid = ms;
            }
        }

        int sx = subVoxel(loc.x - pos.getX(), hit.getDirection().getStepX(), grid);
        int sy = subVoxel(loc.y - pos.getY(), hit.getDirection().getStepY(), grid);
        int sz = subVoxel(loc.z - pos.getZ(), hit.getDirection().getStepZ(), grid);

        double sub = 1.0 / grid;
        double minX = pos.getX() + sx * sub - cam.x;
        double minY = pos.getY() + sy * sub - cam.y;
        double minZ = pos.getZ() + sz * sub - cam.z;
        double maxX = minX + sub;
        double maxY = minY + sub;
        double maxZ = minZ + sub;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();

        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        renderLineBox(poseStack, lines, minX, minY, minZ, maxX, maxY, maxZ,
                OUTLINE_R, OUTLINE_G, OUTLINE_B, OUTLINE_A);

        if (digPos != null && digPos.equals(pos)
                && digSubX == sx && digSubY == sy && digSubZ == sz
                && digProgress > 0f) {
            renderDigOverlay(poseStack, buffer,
                    minX, minY, minZ, maxX, maxY, maxZ, digProgress);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        if (!ShrinkRayItem.isShrunk(player)) { resetDig(); return; }

        boolean holdingAttack = mc.screen == null && mc.options.keyAttack.isDown();
        HitResult hit = mc.hitResult;

        if (!holdingAttack || !(hit instanceof BlockHitResult bhr)) {
            resetDig();
            return;
        }

        BlockPos hitPos = bhr.getBlockPos();
        Level level = mc.level;
        BlockState state = level.getBlockState(hitPos);
        if (state.isAir()) { resetDig(); return; }

        int grid = SUB_GRID;
        if (state.getBlock() instanceof MiniatureBlock) {
            if (!(level.getBlockEntity(hitPos) instanceof MiniatureBlockEntity mini)) {
                resetDig(); return;
            }
            int ms = mini.getScale();
            if (ms > 0) grid = ms;
        }

        int sx = subVoxel(bhr.getLocation().x - hitPos.getX(), bhr.getDirection().getStepX(), grid);
        int sy = subVoxel(bhr.getLocation().y - hitPos.getY(), bhr.getDirection().getStepY(), grid);
        int sz = subVoxel(bhr.getLocation().z - hitPos.getZ(), bhr.getDirection().getStepZ(), grid);

        if (digPos == null || !digPos.equals(hitPos)
                || digSubX != sx || digSubY != sy || digSubZ != sz) {

            BlockState toolState;
            float hardness;

            if (state.getBlock() instanceof MiniatureBlock) {
                MiniatureBlockEntity mini = (MiniatureBlockEntity) level.getBlockEntity(hitPos);
                if (mini == null) { resetDig(); return; }

                int ms = mini.getScale();
                if (ms <= 0) { resetDig(); return; }

                int voxelIdx = (sy * ms + sz) * ms + sx;
                MiniatureVoxel voxel = mini.getVoxels().get(voxelIdx);
                if (voxel == null) { resetDig(); return; }

                toolState = voxel.state();
                if (toolState == null) { resetDig(); return; }

                hardness = mini.getVoxelHardness(voxelIdx);
                if (hardness < 0f) {
                    hardness = toolState.getDestroySpeed(level, hitPos);
                }
            } else {
                toolState = state;
                hardness = state.getDestroySpeed(level, hitPos);
            }

            if (hardness < 0f) { resetDig(); return; }
            if (hardness == 0f) hardness = 0.01f;

            digPos = hitPos.immutable();
            digSource = toolState;
            digHardness = hardness;
            digSubX = sx; digSubY = sy; digSubZ = sz;
            digFace = bhr.getDirection();
            digProgress = 0f;

            if (!(state.getBlock() instanceof MiniatureBlock)) {
                BlockPos microOrigin = new BlockPos(
                        hitPos.getX() * SCALE, hitPos.getY() * SCALE, hitPos.getZ() * SCALE);
                short[] pixelData = BlockPixelSampler.sampleBlock(
                        state, SCALE, hitPos.asLong(), hitPos, level);
                NetworkHelper.sendStartDigging(hitPos, state, SCALE, pixelData, hitPos);
            }
            return;
        }

        if (state.getBlock() instanceof MiniatureBlock
                && level.getBlockEntity(hitPos) instanceof MiniatureBlockEntity mini) {
            int ms = mini.getScale();
            if (ms > 0) {
                int voxelIdx = (sy * ms + sz) * ms + sx;
                float h = mini.getVoxelHardness(voxelIdx);
                if (h >= 0f) digHardness = h;
            }
        }

        float speed = player.getDestroySpeed(digSource);
        float damagePerTick = speed / digHardness / 30.0f;
        digProgress += damagePerTick;

        if (digProgress >= 1.0f) {
            NetworkHelper.sendBreakSubVoxel(digPos, digSubX, digSubY, digSubZ);
            resetDig();
        }
    }

    private static void resetDig() {
        digPos = null;
        digSource = null;
        digFace = null;
        digProgress = 0f;
        digHardness = 1.0f;
    }

    private static int subVoxel(double local, int step, int grid) {
        double adj = local;
        if (step > 0) adj = Math.nextDown(local);
        else if (step < 0) adj = Math.nextUp(local);
        int idx = (int) Math.floor(adj * grid);
        if (idx < 0) idx = 0;
        if (idx >= grid) idx = grid - 1;
        return idx;
    }

    private static void renderLineBox(PoseStack poseStack, VertexConsumer c,
                                      double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ,
                                      float r, float g, float b, float a) {
        Matrix4f m = poseStack.last().pose();
        float x0 = (float) minX, y0 = (float) minY, z0 = (float) minZ;
        float x1 = (float) maxX, y1 = (float) maxY, z1 = (float) maxZ;

        line(c, m, x0, y0, z0, x1, y0, z0, r, g, b, a);
        line(c, m, x1, y0, z0, x1, y0, z1, r, g, b, a);
        line(c, m, x1, y0, z1, x0, y0, z1, r, g, b, a);
        line(c, m, x0, y0, z1, x0, y0, z0, r, g, b, a);

        line(c, m, x0, y1, z0, x1, y1, z0, r, g, b, a);
        line(c, m, x1, y1, z0, x1, y1, z1, r, g, b, a);
        line(c, m, x1, y1, z1, x0, y1, z1, r, g, b, a);
        line(c, m, x0, y1, z1, x0, y1, z0, r, g, b, a);

        line(c, m, x0, y0, z0, x0, y1, z0, r, g, b, a);
        line(c, m, x1, y0, z0, x1, y1, z0, r, g, b, a);
        line(c, m, x1, y0, z1, x1, y1, z1, r, g, b, a);
        line(c, m, x0, y0, z1, x0, y1, z1, r, g, b, a);
    }

    private static void line(VertexConsumer c, Matrix4f m,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float r, float g, float b, float a) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0f) { dx /= len; dy /= len; dz /= len; }
        c.addVertex(m, x0, y0, z0).setColor(r, g, b, a).setNormal(dx, dy, dz);
        c.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setNormal(dx, dy, dz);
    }

    private static void renderDigOverlay(PoseStack poseStack, MultiBufferSource buffer,
                                         double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ,
                                         float progress) {
        int stage = (int) (progress * 10);
        if (stage < 0) stage = 0;
        if (stage > 9) stage = 9;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ResourceLocation.withDefaultNamespace("block/destroy_stage_" + stage));

        VertexConsumer c = buffer.getBuffer(RenderType.crumbling(InventoryMenu.BLOCK_ATLAS));
        Matrix4f m = poseStack.last().pose();

        float x0 = (float) minX, y0 = (float) minY, z0 = (float) minZ;
        float x1 = (float) maxX, y1 = (float) maxY, z1 = (float) maxZ;
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();

        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;

        // UP
        quad(c, m, light, overlay, 0f, 1f, 0f,
                x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
                u0, v1, u0, v0, u1, v0, u1, v1);

        // DOWN
        quad(c, m, light, overlay, 0f, -1f, 0f,
                x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                u0, v0, u1, v0, u1, v1, u0, v1);

        // NORTH
        quad(c, m, light, overlay, 0f, 0f, -1f,
                x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0,
                u0, v1, u0, v0, u1, v0, u1, v1);

        // SOUTH
        quad(c, m, light, overlay, 0f, 0f, 1f,
                x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                u0, v1, u1, v1, u1, v0, u0, v0);

        // WEST
        quad(c, m, light, overlay, -1f, 0f, 0f,
                x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                u0, v1, u1, v1, u1, v0, u0, v0);

        // EAST
        quad(c, m, light, overlay, 1f, 0f, 0f,
                x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1,
                u0, v1, u0, v0, u1, v0, u1, v1);
    }

    private static void quad(VertexConsumer c, Matrix4f m,
                             int light, int overlay,
                             float nx, float ny, float nz,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float u0, float v0, float u1, float v1,
                             float u2, float v2, float u3, float v3) {
        c.addVertex(m, x0, y0, z0).setColor(1f, 1f, 1f, 1f).setUv(u0, v0)
                .setLight(light).setOverlay(overlay).setNormal(nx, ny, nz);
        c.addVertex(m, x1, y1, z1).setColor(1f, 1f, 1f, 1f).setUv(u1, v1)
                .setLight(light).setOverlay(overlay).setNormal(nx, ny, nz);
        c.addVertex(m, x2, y2, z2).setColor(1f, 1f, 1f, 1f).setUv(u2, v2)
                .setLight(light).setOverlay(overlay).setNormal(nx, ny, nz);
        c.addVertex(m, x3, y3, z3).setColor(1f, 1f, 1f, 1f).setUv(u3, v3)
                .setLight(light).setOverlay(overlay).setNormal(nx, ny, nz);
    }
}
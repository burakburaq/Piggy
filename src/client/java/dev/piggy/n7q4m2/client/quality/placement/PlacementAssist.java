package dev.piggy.n7q4m2.client.quality.placement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.piggy.n7q4m2.client.quality.QualityClient;
import dev.piggy.n7q4m2.quality.placement.PlacementScan;
import dev.piggy.n7q4m2.client.quality.placement.mixin.BlockItemPlacementAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A client-side aiming aid. Actual placements still run through Minecraft's ordinary use-item path. */
public final class PlacementAssist {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static Target preview;
    private static long nextPreviewNanos;
    private static long worldRevision, previewRevision;
    private static int previewTick;
    private static Object previewPlayer, previewLevel;
    private static Vec3 previewEye, previewView, previewHit;
    private static HitResult.Type previewHitType;
    private static ItemStack previewStack = ItemStack.EMPTY;
    private static InteractionHand previewHand;
    private static double previewReach;
    private static Direction bridgeDirection;
    private static int bridgeY;
    private static float bridgeYaw;
    private static Identifier bridgeDimension;
    private static java.util.UUID bridgePlayer;
    private static InteractionHand bridgeHand;
    private PlacementAssist() {}

    public record Target(BlockPos position, BlockHitResult anchorHit, InteractionHand hand, BlockState state,
                         VoxelShape shape, double distanceSquared, int tint) {}

    public static void initClient() {
        LevelRenderEvents.COLLECT_SUBMITS.register(PlacementAssist::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            preview = null; resetBridge(); clearPreviewCache();
        });
    }

    public static Target preview() { return preview; }
    public static boolean bridging() { return bridgeDirection != null; }
    public static void refreshUseLatch(Minecraft mc) {
        if (!bridging()) return;
        if (!mc.options.keyUse.isDown() || mc.player == null || mc.level == null || mc.gui.screen() != null
                || !mc.player.isAlive() || mc.player.isPassenger() || mc.getCameraEntity() != mc.player
                || !QualityClient.config.placementAssist || !mc.player.getUUID().equals(bridgePlayer)
                || !mc.level.dimension().identifier().equals(bridgeDimension)) resetBridge();
    }
    public static void beginBridge(Minecraft mc, Target target) {
        if (bridging() || !mc.options.keyUse.isDown()) return;
        bridgeDirection = target.anchorHit().getDirection();
        bridgeY = target.position().getY();
        bridgeYaw = mc.player.getYRot();
        bridgeDimension = mc.level.dimension().identifier();
        bridgePlayer = mc.player.getUUID();
        bridgeHand = target.hand();
        nextPreviewNanos = 0;
        worldChanged();
    }
    private static void resetBridge() {
        bridgeDirection = null; bridgeDimension = null; bridgePlayer = null; bridgeHand = null;
        nextPreviewNanos = 0;
        worldChanged();
    }
    public static void worldChanged() { worldRevision++; }
    private static void clearPreviewCache() {
        previewPlayer = null; previewLevel = null; previewEye = null; previewView = null; previewHit = null;
        previewStack = ItemStack.EMPTY; previewHand = null;
    }
    public static boolean hasHeldBlock(Minecraft mc) { return mc.player != null && heldBlockHand(mc) != null; }

    public static void updatePreview(Minecraft minecraft, float partialTick) {
        refreshUseLatch(minecraft);
        if (!QualityClient.config.placementAssist || minecraft.player == null || minecraft.level == null
                || minecraft.gui.screen() != null || minecraft.getCameraEntity() != minecraft.player || !hasHeldBlock(minecraft)
                || minecraft.player.isSpectator() || minecraft.player.isHandsBusy()
                || minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.ENTITY) {
            preview = null; nextPreviewNanos = 0; clearPreviewCache(); return;
        }
        // Preview is visual only: cap its world queries at 60 Hz even on very high FPS clients.
        // findTarget before each actual click remains uncached and validates the current world/aim.
        long now = System.nanoTime();
        if (now < nextPreviewNanos) return;
        nextPreviewNanos = now + 16_666_667L;
        var player = minecraft.player;
        Vec3 eye = player.getEyePosition(partialTick), view = player.getViewVector(partialTick);
        var hand = heldBlockHand(minecraft); var stack = player.getItemInHand(hand);
        var hit = minecraft.hitResult;
        var hitType = hit == null ? null : hit.getType();
        Vec3 hitPoint = hit == null ? null : hit.getLocation();
        double reach = player.blockInteractionRange();
        boolean sameStack = ItemStack.matches(previewStack, stack);
        // A tick boundary covers moving entities; block updates also invalidate within the same tick.
        if (previewPlayer == player && previewLevel == minecraft.level && previewTick == player.tickCount
                && previewRevision == worldRevision && eye.equals(previewEye) && view.equals(previewView)
                && hitType == previewHitType && java.util.Objects.equals(hitPoint, previewHit)
                && hand == previewHand && reach == previewReach && sameStack) return;
        previewPlayer = player; previewLevel = minecraft.level; previewTick = player.tickCount; previewRevision = worldRevision;
        previewEye = eye; previewView = view; previewHitType = hitType; previewHit = hitPoint;
        previewHand = hand; previewReach = reach;
        if (!sameStack) previewStack = stack.copy();
        preview = findTarget(minecraft, partialTick);
    }

    /** Recompute immediately before use so a stale frame cannot place a block after looking away. */
    public static Target findTarget(Minecraft mc, float partialTick) {
        if (!QualityClient.config.placementAssist || mc.player == null || mc.level == null || mc.gameMode == null
                || mc.gui.screen() != null || mc.player.isSpectator() || mc.player.isHandsBusy()
                || mc.getCameraEntity() != mc.player || (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY)) return null;
        InteractionHand hand = heldBlockHand(mc);
        if (hand == null) return null;
        if (bridging() && (hand != bridgeHand || Math.abs(Mth.wrapDegrees(mc.player.getYRot() - bridgeYaw)) >= 45)) return null;
        ItemStack stack = mc.player.getItemInHand(hand);
        if (!stack.isItemEnabled(mc.level.enabledFeatures())) return null;
        BlockItem item = (BlockItem) stack.getItem();
        Vec3 eye = mc.player.getEyePosition(partialTick);
        double reach = mc.player.blockInteractionRange();
        Vec3 end = eye.add(mc.player.getViewVector(partialTick).scale(reach));
        double normalDistance = mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS
                ? reach * reach : eye.distanceToSqr(mc.hitResult.getLocation());
        int radius = (int) Math.ceil(reach);
        int centerX = mc.player.blockPosition().getX(), centerZ = mc.player.blockPosition().getZ();
        // Keep the latched deck level through jumps. Before latching, also look below airborne feet.
        int topY = bridging() ? bridgeY : (int) Math.floor(mc.player.getY() - 0.05);
        int bottomY = bridging() || mc.player.onGround() ? topY : topY - 2;
        Target nearest = null;
        for (int y = topY; y >= bottomY; y--) {
        var bounds = PlacementScan.layer(eye.x, eye.y, eye.z, end.x, end.y, end.z, y, centerX, centerZ, radius);
        if (bounds == null) continue;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                BlockPos target = new BlockPos(x, y, z);
                AABB volume = new AABB(target);
                var intersection = volume.clip(eye, end);
                if (intersection.isEmpty() || volume.intersects(mc.player.getBoundingBox())
                        || !mc.level.getWorldBorder().isWithinBounds(target) || mc.level.isOutsideBuildHeight(target)) continue;
                double distance = eye.distanceToSqr(intersection.get());
                if (distance >= normalDistance - 0.0001 || nearest != null && distance >= nearest.distanceSquared()) continue;
                BlockHitResult sight = mc.level.clip(new ClipContext(eye, intersection.get(), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, mc.player));
                if (sight.getType() != HitResult.Type.MISS && eye.distanceToSqr(sight.getLocation()) < distance - 0.0001) continue;
                for (Direction direction : HORIZONTAL) {
                    if (bridging() && direction != bridgeDirection) continue;
                    BlockPos anchor = target.relative(direction.getOpposite());
                    BlockState support = mc.level.getBlockState(anchor);
                    if (!support.isCollisionShapeFullBlock(mc.level, anchor)) continue;
                    if (!mc.player.isShiftKeyDown() && (support.hasBlockEntity()
                            || support.getMenuProvider(mc.level, anchor) != null || support.getBlock() instanceof RespawnAnchorBlock)) continue;
                    Vec3 face = Vec3.atCenterOf(anchor).add(direction.getStepX() * 0.5,
                            direction.getStepY() * 0.5, direction.getStepZ() * 0.5);
                    if (eye.distanceToSqr(face) > reach * reach || !mc.player.isWithinBlockInteractionRange(anchor, 0.0)
                            || !mc.level.mayInteract(mc.player, anchor) || !mc.player.mayUseItemAt(anchor, direction, stack)) continue;
                    // The support itself may meet the ray at its top edge; an intervening block never may.
                    BlockHitResult anchorSight = mc.level.clip(new ClipContext(eye,
                            face.add(direction.getStepX() * 0.0001, 0, direction.getStepZ() * 0.0001),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                    if (anchorSight.getType() == HitResult.Type.BLOCK && !anchorSight.getBlockPos().equals(anchor)) continue;
                    BlockHitResult hit = new BlockHitResult(face, direction, anchor, false);
                    BlockPlaceContext context = item.updatePlacementContext(new BlockPlaceContext(mc.player, hand, stack, hit));
                    if (context == null || !context.getClickedPos().equals(target) || !context.canPlace()) continue;
                    BlockState state = ((BlockItemPlacementAccessor) item).piggy$getPlacementState(context);
                    if (state == null) continue;
                    VoxelShape shape = state.getShape(mc.level, target, CollisionContext.of(mc.player));
                    if (shape.isEmpty()) shape = Shapes.block();
                    int tint = state.getMapColor(mc.level, target).col;
                    nearest = new Target(target, hit, hand, state, shape, distance, tint == 0 ? 0x70ade9 : tint);
                    break;
                }
            }
        }
        }
        return nearest;
    }

    private static InteractionHand heldBlockHand(Minecraft mc) {
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof BlockItem) return InteractionHand.MAIN_HAND;
        // A food, bow, or other usable main-hand item must keep its regular right-click action.
        return main.isEmpty() && mc.player.getOffhandItem().getItem() instanceof BlockItem ? InteractionHand.OFF_HAND : null;
    }

    private static void render(LevelRenderContext context) {
        Target ghost = preview;
        if (ghost == null || !QualityClient.config.placementAssist) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poses = context.poseStack();
        poses.pushPose();
        poses.translate(ghost.position().getX() - camera.x, ghost.position().getY() - camera.y, ghost.position().getZ() - camera.z);
        int color = 0x38000000 | ghost.tint();
        context.submitNodeCollector().submitCustomGeometry(poses, RenderTypes.debugQuads(), (pose, fill) ->
            ghost.shape().forAllBoxes((x0, y0, z0, x1, y1, z1) -> box(pose, fill, color,
                (float)x0, (float)y0, (float)z0, (float)x1, (float)y1, (float)z1)));
        context.submitNodeCollector().submitShapeOutline(poses, ghost.shape(), RenderTypes.lines(), 0xB0BDE8FF, 2.0F, false);
        poses.popPose();
    }

    private static void box(PoseStack.Pose pose, VertexConsumer vertices, int color,
                            float x0, float y0, float z0, float x1, float y1, float z1) {
        quad(pose, vertices, color, x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0);
        quad(pose, vertices, color, x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1);
        quad(pose, vertices, color, x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1);
        quad(pose, vertices, color, x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0);
        quad(pose, vertices, color, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1);
        quad(pose, vertices, color, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices, int color,
                             float ax,float ay,float az, float bx,float by,float bz,
                             float cx,float cy,float cz, float dx,float dy,float dz) {
        vertices.addVertex(pose, ax, ay, az).setColor(color);
        vertices.addVertex(pose, bx, by, bz).setColor(color);
        vertices.addVertex(pose, cx, cy, cz).setColor(color);
        vertices.addVertex(pose, dx, dy, dz).setColor(color);
    }
}

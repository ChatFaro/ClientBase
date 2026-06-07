package cn.clientbase.util.render;

import cn.clientbase.util.IMinecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

@UtilityClass
public final class RenderUtil implements IMinecraft {
    public static final Matrix4f lastProjMat = new Matrix4f();
    public static final Matrix4f lastModMat = new Matrix4f();
    public static final Matrix4f lastWorldSpaceMatrix = new Matrix4f();

    public @NotNull Vec3d worldSpaceToScreenSpace(@NotNull Vec3d pos) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        int displayHeight = mc.getWindow().getHeight();
        int[] viewport = {0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight()};
        Vector3f target = new Vector3f();
        double deltaX = pos.x - camera.getPos().x;
        double deltaY = pos.y - camera.getPos().y;
        double deltaZ = pos.z - camera.getPos().z;
        Vector4f transformedCoordinates = new Vector4f((float) deltaX, (float) deltaY, (float) deltaZ, 1.0f).mul(lastWorldSpaceMatrix);
        Matrix4f combinedMatrix = new Matrix4f(lastProjMat).mul(lastModMat);
        combinedMatrix.project(transformedCoordinates.x(), transformedCoordinates.y(), transformedCoordinates.z(), viewport, target);
        double scaleFactor = mc.getWindow().getScaleFactor();
        return new Vec3d(target.x / scaleFactor, (displayHeight - target.y) / scaleFactor, target.z);
    }

    public @NotNull Vec3d[] getVectors(@NotNull Entity ent) {
        double x = ent.prevX + (ent.getX() - ent.prevX) * mc.getRenderTickCounter().getTickDelta(true);
        double y = ent.prevY + (ent.getY() - ent.prevY) * mc.getRenderTickCounter().getTickDelta(true);
        double z = ent.prevZ + (ent.getZ() - ent.prevZ) * mc.getRenderTickCounter().getTickDelta(true);
        Box axisAlignedBB2 = ent.getBoundingBox();
        Box axisAlignedBB = new Box(axisAlignedBB2.minX - ent.getX() + x - 0.05, axisAlignedBB2.minY - ent.getY() + y, axisAlignedBB2.minZ - ent.getZ() + z - 0.05, axisAlignedBB2.maxX - ent.getX() + x + 0.05, axisAlignedBB2.maxY - ent.getY() + y + 0.15, axisAlignedBB2.maxZ - ent.getZ() + z + 0.05);
        return new Vec3d[]{new Vec3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ)};
    }

    public void drawRect(DrawContext context, float x, float y, float width, float height, int color) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        context.draw(vertexConsumers -> {
            VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getGui());
            buffer.vertex(matrix, 0, 0, 0).color(color);
            buffer.vertex(matrix, 0, height, 0).color(color);
            buffer.vertex(matrix, width, height, 0).color(color);
            buffer.vertex(matrix, width, 0, 0).color(color);
        });

        context.getMatrices().pop();
    }

    public void drawGradientRect(DrawContext context, float x, float y, float width, float height, int startColor, int endColor, boolean horizontal) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        context.draw(vertexConsumers -> {
            VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getGui());
            if (horizontal) {
                buffer.vertex(matrix, 0, 0, 0).color(startColor);
                buffer.vertex(matrix, 0, height, 0).color(startColor);
                buffer.vertex(matrix, width, height, 0).color(endColor);
                buffer.vertex(matrix, width, 0, 0).color(endColor);
            } else {
                buffer.vertex(matrix, 0, 0, 0).color(startColor);
                buffer.vertex(matrix, 0, height, 0).color(endColor);
                buffer.vertex(matrix, width, height, 0).color(endColor);
                buffer.vertex(matrix, width, 0, 0).color(startColor);
            }
        });

        context.getMatrices().pop();
    }

    // -----------------------------------------------------------------------
    // 3D world-space box rendering (ported from OpenZen RenderUtil) — used by
    // Scaffold's placement preview. Caller is responsible for translating the
    // MatrixStack to camera-relative space before calling.
    // -----------------------------------------------------------------------

    /** Filled box (QUADS). Color/blend set via RenderSystem.setShaderColor beforehand. */
    public void drawSolidBox(Box box, MatrixStack matrices) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        RenderSystem.setShader(ShaderProgramKeys.POSITION);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;
        // bottom
        buffer.vertex(matrix, minX, minY, minZ);
        buffer.vertex(matrix, maxX, minY, minZ);
        buffer.vertex(matrix, maxX, minY, maxZ);
        buffer.vertex(matrix, minX, minY, maxZ);
        // top
        buffer.vertex(matrix, minX, maxY, minZ);
        buffer.vertex(matrix, minX, maxY, maxZ);
        buffer.vertex(matrix, maxX, maxY, maxZ);
        buffer.vertex(matrix, maxX, maxY, minZ);
        // north
        buffer.vertex(matrix, minX, minY, minZ);
        buffer.vertex(matrix, minX, maxY, minZ);
        buffer.vertex(matrix, maxX, maxY, minZ);
        buffer.vertex(matrix, maxX, minY, minZ);
        // east
        buffer.vertex(matrix, maxX, minY, minZ);
        buffer.vertex(matrix, maxX, maxY, minZ);
        buffer.vertex(matrix, maxX, maxY, maxZ);
        buffer.vertex(matrix, maxX, minY, maxZ);
        // south
        buffer.vertex(matrix, maxX, minY, maxZ);
        buffer.vertex(matrix, maxX, maxY, maxZ);
        buffer.vertex(matrix, minX, maxY, maxZ);
        buffer.vertex(matrix, minX, minY, maxZ);
        // west
        buffer.vertex(matrix, minX, minY, maxZ);
        buffer.vertex(matrix, minX, maxY, maxZ);
        buffer.vertex(matrix, minX, maxY, minZ);
        buffer.vertex(matrix, minX, minY, minZ);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    /** Outline box (DEBUG_LINES). Color/blend set via RenderSystem.setShaderColor beforehand. */
    public void drawOutlineBox(Box box, MatrixStack matrices) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        RenderSystem.setShader(ShaderProgramKeys.POSITION);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;
        // bottom rectangle
        buffer.vertex(matrix, minX, minY, minZ); buffer.vertex(matrix, maxX, minY, minZ);
        buffer.vertex(matrix, maxX, minY, minZ); buffer.vertex(matrix, maxX, minY, maxZ);
        buffer.vertex(matrix, maxX, minY, maxZ); buffer.vertex(matrix, minX, minY, maxZ);
        buffer.vertex(matrix, minX, minY, maxZ); buffer.vertex(matrix, minX, minY, minZ);
        // verticals
        buffer.vertex(matrix, minX, minY, minZ); buffer.vertex(matrix, minX, maxY, minZ);
        buffer.vertex(matrix, maxX, minY, minZ); buffer.vertex(matrix, maxX, maxY, minZ);
        buffer.vertex(matrix, maxX, minY, maxZ); buffer.vertex(matrix, maxX, maxY, maxZ);
        buffer.vertex(matrix, minX, minY, maxZ); buffer.vertex(matrix, minX, maxY, maxZ);
        // top rectangle
        buffer.vertex(matrix, minX, maxY, minZ); buffer.vertex(matrix, maxX, maxY, minZ);
        buffer.vertex(matrix, maxX, maxY, minZ); buffer.vertex(matrix, maxX, maxY, maxZ);
        buffer.vertex(matrix, maxX, maxY, maxZ); buffer.vertex(matrix, minX, maxY, maxZ);
        buffer.vertex(matrix, minX, maxY, maxZ); buffer.vertex(matrix, minX, maxY, minZ);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
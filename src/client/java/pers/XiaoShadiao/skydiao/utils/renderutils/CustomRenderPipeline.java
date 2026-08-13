package pers.XiaoShadiao.skydiao.utils.renderutils;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.apiimpl.IrisApiV0Impl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.util.*;

public class    CustomRenderPipeline {

    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    // :::custom-pipelines:define-pipeline
    public static final RenderPipeline THROUGH_WALLS_FILL = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("skydiao", "pipeline/THROUGH_WALLS_FILL".toLowerCase()))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
            .withDepthStencilState(Optional.empty())
            .build()
    );
    public static final RenderPipeline NO_THROUGH_WALLS_FILL = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("skydiao", "pipeline/NO_THROUGH_WALLS_FILL".toLowerCase()))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build()
    );

    public static final RenderPipeline THROUGH_WALLS_LINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("skydiao", "pipeline/THROUGH_WALLS_LINE".toLowerCase()))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(Optional.empty())
                    .build()
    );

    public static final RenderPipeline NO_THROUGH_WALLS_LINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("skydiao", "pipeline/NO_THROUGH_WALLS_LINE".toLowerCase()))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .build()
    );

    static {
        try {
            if(FabricLoader.getInstance().isModLoaded("iris")) initIrisShader();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void renderCircle(LevelRenderContext context, RenderPipeline pipeline, Vec3 center, float radius, int segments){
        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;

        assert matrices != null;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        if (buffer == null) {
            buffer = new BufferBuilder(allocator, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
        }

        renderCircle1(buffer, matrices.last(), center, radius, segments);

        matrices.popPose();

    }

    public static void renderCircle1(VertexConsumer vertexConsumer, PoseStack.Pose pose,
                                     Vec3 center, float radius, int segments) {


        float angleStep = (float) (2 * Math.PI / segments);

        for (int i = 0; i < segments; i++) {
            float angle1 = i * angleStep;
            float angle2 = (i+1) * angleStep;

            float x1 = (float) (center.x + radius * Math.cos(angle1));
            float z1 = (float) (center.z + radius * Math.sin(angle1));
            float x2 = (float) (center.x + radius * Math.cos(angle2));
            float z2 = (float) (center.z + radius * Math.sin(angle2));
            float y = (float) center.y;

            vertexConsumer.addVertex(pose, (float) center.x, y, (float) center.z)
                    .setColor(0.0f, 1.0f, 0.0f, 0.3f);
            vertexConsumer.addVertex(pose, x1, y, z1)
                    .setColor(0.0f, 1.0f, 0.0f, 0.3f);
            vertexConsumer.addVertex(pose, x2, y, z2)
                    .setColor(0.0f, 1.0f, 0.0f, 0.3f);
        }
    }

    private static void initIrisShader() {
        IrisApiV0Impl.INSTANCE.assignPipeline(THROUGH_WALLS_FILL, IrisProgram.BASIC);
        IrisApiV0Impl.INSTANCE.assignPipeline(NO_THROUGH_WALLS_FILL, IrisProgram.BASIC);
        IrisApiV0Impl.INSTANCE.assignPipeline(THROUGH_WALLS_LINE, IrisProgram.LINES);
        IrisApiV0Impl.INSTANCE.assignPipeline(NO_THROUGH_WALLS_LINE, IrisProgram.LINES);
    }

    // :::custom-pipelines:define-pipeline
    // :::custom-pipelines:extraction-phase
    private final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private BufferBuilder buffer;

    // :::custom-pipelines:extraction-phase
    // :::custom-pipelines:drawing-phase
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private XSDMappableRingBuffer vertexBuffer;


    private static final Map<RenderPipeline, CustomRenderPipeline> cache = new HashMap<>();
    // :::custom-pipelines:drawing-phase
    public static CustomRenderPipeline getInstance(RenderPipeline pipeline) {
        return cache.computeIfAbsent(pipeline, p -> new CustomRenderPipeline());
    }
    public static void closeAll() {
        cache.values().forEach(CustomRenderPipeline::close);
        cache.clear();
    }

    // :::custom-pipelines:extraction-phase
    public void renderESP(LevelRenderContext context, RenderPipeline pipeline, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a, boolean fillBox) {
        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;

        assert matrices != null;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        if (buffer == null) {
            buffer = new BufferBuilder(allocator, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
        }

        if (fillBox) {
            XSDShapeRenderer.addChainedFilledBoxVertices(matrices, buffer, x1, y1, z1, x2, y2, z2, r, g, b, a / 2);
        } else {
            XSDShapeRenderer.renderLineBox(matrices.last(), buffer, x1, y1, z1, x2, y2, z2, r, g, b, a);
        }

        matrices.popPose();
    }

    public void renderTrace(LevelRenderContext context, RenderPipeline pipeline, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;

        assert matrices != null;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        if (buffer == null) {
            buffer = new BufferBuilder(allocator, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
        }

//        for (float i = -0.0005f; i <= 0.0005f; i += 0.0005f) {
//            XSDShapeRenderer.renderVector(matrices, buffer, new Vector3f(x1 - i, y1, z1 - i), new Vec3(x2 - i, y2, z2 - i), new Color(r, g, b, a).getRGB());
//            XSDShapeRenderer.renderVector(matrices, buffer, new Vector3f(x1 - i, y1, z1 + i), new Vec3(x2 - i, y2, z2 + i), new Color(r, g, b, a).getRGB());
//        }
        XSDShapeRenderer.renderVector(matrices, buffer, new Vector3f(x1, y1, z1), new Vec3(x2, y2, z2), new Color(r, g, b, a).getRGB());

        matrices.popPose();
    }

    public void renderWorldLine(LevelRenderContext context, RenderPipeline pipeline, float x1, float y1, float z1, float x2, float y2, float z2, float r1, float g1, float b1, float a1, float r2, float g2, float b2, float a2) {
        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;

        assert matrices != null;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        if (buffer == null) {
            buffer = new BufferBuilder(allocator, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
        }
        int rgb1 = new Color(r1, g1, b1, a1).getRGB();
        int rgb2 = new Color(r2, g2, b2, a2).getRGB();

        RenderUtils.renderVector(matrices, buffer, new Vector3f(x1, y1, z1), new Vec3(x2, y2, z2), rgb1, rgb2);
        matrices.popPose();
    }
    // :::custom-pipelines:extraction-phase

    // :::custom-pipelines:drawing-phase
    public void draw(Minecraft client, @SuppressWarnings("SameParameterValue") RenderPipeline pipeline) {
        // Build the buffer
        RenderSystem.assertOnRenderThread();
        try {
            if (buffer == null) return;

            MeshData builtBuffer = buffer.buildOrThrow();
            MeshData.DrawState drawParameters = builtBuffer.drawState();
            VertexFormat format = drawParameters.format();

            GpuBuffer vertices = upload(drawParameters, format, builtBuffer);

            draw(client, pipeline, builtBuffer, drawParameters, vertices, format);

            // Rotate the vertex buffer so we are less likely to use buffers that the GPU is using
            vertexBuffer.rotate();
        } finally {
            buffer = null;
        }
    }

    private GpuBuffer upload(MeshData.DrawState drawParameters, VertexFormat format, MeshData builtBuffer) {
        // Calculate the size needed for the vertex buffer
        RenderSystem.assertOnRenderThread();
        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

        // Initialize or resize the vertex buffer as needed
        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }

            vertexBuffer = new XSDMappableRingBuffer(() -> "SkyDiao render pipeline", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize, 10);
        }

        // Copy vertex data into the vertex buffer
        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }

        return vertexBuffer.currentBuffer();
    }

    private void draw(Minecraft client, RenderPipeline pipeline, MeshData builtBuffer, MeshData.DrawState drawParameters, GpuBuffer vertices, VertexFormat format) {
        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            // Sort the quads if there is translucency
            builtBuffer.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting());
            // Upload the index buffer
            indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
            indexType = builtBuffer.drawState().indexType();
        } else {
            // Use the general shape index buffer for non-quad draw modes
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        // Actually execute the draw
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "SkyDiao render pipeline rendering", client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(), client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            // Bind texture if applicable:
            // Sampler0 is used for texture inputs in vertices
            // renderPass.bindSampler("Sampler0", textureView);

            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);

            // The base vertex is the starting index when we copied the data into the vertex buffer divided by vertex size
            //noinspection ConstantValue
            renderPass.drawIndexed(0 / format.getVertexSize(), 0, drawParameters.indexCount(), 1);
        }

        builtBuffer.close();
    }
    // :::custom-pipelines:drawing-phase

    // :::custom-pipelines:clean-up
    public void close() {
        allocator.close();

        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }

        buffer = null;
    }

    // :::custom-pipelines:clean-up


    // 复制粘贴

    public static class XSDShapeRenderer {
        public static void renderShape(PoseStack poseStack, VertexConsumer vertexConsumer, VoxelShape voxelShape, double d, double e, double f, int i) {
            PoseStack.Pose pose = poseStack.last();
            voxelShape.forAllEdges((g, h, j, k, l, m) -> {
                Vector3f vector3f = new Vector3f((float)(k - g), (float)(l - h), (float)(m - j)).normalize();
                vertexConsumer.addVertex(pose, (float)(g + d), (float)(h + e), (float)(j + f)).setColor(i).setNormal(pose, vector3f);
                vertexConsumer.addVertex(pose, (float)(k + d), (float)(l + e), (float)(m + f)).setColor(i).setNormal(pose, vector3f);
            });
        }

        public static void renderLineBox(PoseStack.Pose pose, VertexConsumer vertexConsumer, AABB aABB, float f, float g, float h, float i) {
            renderLineBox(pose, vertexConsumer, aABB.minX, aABB.minY, aABB.minZ, aABB.maxX, aABB.maxY, aABB.maxZ, f, g, h, i, f, g, h);
        }

        public static void renderLineBox(
                PoseStack.Pose pose, VertexConsumer vertexConsumer, double d, double e, double f, double g, double h, double i, float j, float k, float l, float m
        ) {
            renderLineBox(pose, vertexConsumer, d, e, f, g, h, i, j, k, l, m, j, k, l);
        }

        public static void renderLineBox(
                PoseStack.Pose pose,
                VertexConsumer vertexConsumer,
                double d,
                double e,
                double f,
                double g,
                double h,
                double i,
                float j,
                float k,
                float l,
                float m,
                float n,
                float o,
                float p
        ) {
            float q = (float)d;
            float r = (float)e;
            float s = (float)f;
            float t = (float)g;
            float u = (float)h;
            float v = (float)i;
            int lineWidth = 3;
            vertexConsumer.addVertex(pose, q, r, s).setColor(j, o, p, m).setNormal(pose, 1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, t, r, s).setColor(j, o, p, m).setNormal(pose, 1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, q, r, s).setColor(n, k, p, m).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, q, u, s).setColor(n, k, p, m).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, q, r, s).setColor(n, o, l, m).setNormal(pose, 0.0F, 0.0F, 1.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, q, r, v).setColor(n, o, l, m).setNormal(pose, 0.0F, 0.0F, 1.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, t, r, s).setColor(j, k, l, m).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, t, u, s).setColor(j, k, l, m).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, t, u, s).setColor(j, k, l, m).setNormal(pose, -1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, q, u, s).setColor(j, k, l, m).setNormal(pose, -1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, q, u, s).setColor(j, k, l, m).setNormal(pose, 0.0F, 0.0F, 1.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, q, u, v).setColor(j, k, l, m).setNormal(pose, 0.0F, 0.0F, 1.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, q, u, v).setColor(j, k, l, m).setNormal(pose, 0.0F, -1.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, q, r, v).setColor(j, k, l, m).setNormal(pose, 0.0F, -1.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, q, r, v).setColor(j, k, l, m).setNormal(pose, 1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, t, r, v).setColor(j, k, l, m).setNormal(pose, 1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, t, r, v).setColor(j, k, l, m).setNormal(pose, 0.0F, 0.0F, -1.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, t, r, s).setColor(j, k, l, m).setNormal(pose, 0.0F, 0.0F, -1.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, q, u, v).setColor(j, k, l, m).setNormal(pose, 1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, t, u, v).setColor(j, k, l, m).setNormal(pose, 1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, t, r, v).setColor(j, k, l, m).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, t, u, v).setColor(j, k, l, m).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, t, u, s).setColor(j, k, l, m).setNormal(pose, 0.0F, 0.0F, 1.0F).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, t, u, v).setColor(j, k, l, m).setNormal(pose, 0.0F, 0.0F, 1.0F).setLineWidth(lineWidth);
        }

        public static void addChainedFilledBoxVertices(
                PoseStack poseStack, VertexConsumer vertexConsumer, double d, double e, double f, double g, double h, double i, float j, float k, float l, float m
        ) {
            addChainedFilledBoxVertices(poseStack, vertexConsumer, (float)d, (float)e, (float)f, (float)g, (float)h, (float)i, j, k, l, m);
        }

        public static void addChainedFilledBoxVertices(
                PoseStack poseStack, VertexConsumer vertexConsumer, float f, float g, float h, float i, float j, float k, float l, float m, float n, float o
        ) {
            Matrix4f matrix4f = poseStack.last().pose();
            vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, g, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, j, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, j, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, g, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, g, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, j, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, j, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, g, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, g, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, g, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, f, j, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, j, h).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
            vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
        }

        public static void renderFace(
                Matrix4f matrix4f,
                VertexConsumer vertexConsumer,
                Direction direction,
                float f,
                float g,
                float h,
                float i,
                float j,
                float k,
                float l,
                float m,
                float n,
                float o
        ) {
            switch (direction) {
                case DOWN:
                    vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, i, g, h).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, f, g, k).setColor(l, m, n, o);
                    break;
                case UP:
                    vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, f, j, k).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, i, j, h).setColor(l, m, n, o);
                    break;
                case NORTH:
                    vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, i, j, h).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, i, g, h).setColor(l, m, n, o);
                    break;
                case SOUTH:
                    vertexConsumer.addVertex(matrix4f, f, g, k).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, f, j, k).setColor(l, m, n, o);
                    break;
                case WEST:
                    vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, f, g, k).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, f, j, k).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
                    break;
                case EAST:
                    vertexConsumer.addVertex(matrix4f, i, g, h).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, i, j, h).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
                    vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
            }
        }

        public static void renderVector(PoseStack poseStack, VertexConsumer vertexConsumer, Vector3f vector3f, Vec3 vec3, int i) {
            PoseStack.Pose pose = poseStack.last();
            int lineWidth = 3;
            vertexConsumer.addVertex(pose, vector3f).setColor(i).setNormal(pose, (float)vec3.x, (float)vec3.y, (float)vec3.z).setLineWidth(lineWidth);
            vertexConsumer.addVertex(pose, (float)(vector3f.x() + vec3.x), (float)(vector3f.y() + vec3.y), (float)(vector3f.z() + vec3.z))
                    .setColor(i)
                    .setNormal(pose, (float)vec3.x, (float)vec3.y, (float)vec3.z).setLineWidth(lineWidth);
        }
    }

}
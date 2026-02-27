package pers.XiaoShadiao.skydiao.utils.renderutils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

public class CustomRenderPipeline {
    // :::custom-pipelines:define-pipeline
    public static final RenderPipeline THROUGH_WALLS_FILL = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("skydiao", "pipeline/THROUGH_WALLS_FILL".toLowerCase()))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build()
    );
    public static final RenderPipeline NO_THROUGH_WALLS_FILL = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("skydiao", "pipeline/NO_THROUGH_WALLS_FILL".toLowerCase()))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
            .withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST)
            .build()
    );

    public static final RenderPipeline THROUGH_WALLS_LINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(ResourceLocation.fromNamespaceAndPath("skydiao", "pipeline/THROUGH_WALLS_LINE".toLowerCase()))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );

    public static final RenderPipeline NO_THROUGH_WALLS_LINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(ResourceLocation.fromNamespaceAndPath("skydiao", "pipeline/NO_THROUGH_WALLS_LINE".toLowerCase()))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST)
                    .build()
    );


    // :::custom-pipelines:define-pipeline
    // :::custom-pipelines:extraction-phase
    private final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private BufferBuilder buffer;

    // :::custom-pipelines:extraction-phase
    // :::custom-pipelines:drawing-phase
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private MappableRingBuffer vertexBuffer;


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
    public void renderESP(WorldRenderContext context, RenderPipeline pipeline, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a, boolean fillBox) {
        PoseStack matrices = context.matrices();
        Vec3 camera = context.worldState().cameraRenderState.pos;

        assert matrices != null;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        if (buffer == null) {
            buffer = new BufferBuilder(allocator, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
        }

        if (fillBox) {
            ShapeRenderer.addChainedFilledBoxVertices(matrices, buffer, x1, y1, z1, x2, y2, z2, r, g, b, a / 2);
        } else {
            for (float i = -0.01f; i <= 0.01f; i += 0.01f) {
                ShapeRenderer.renderLineBox(matrices.last(), buffer, x1 + i, y1 + i, z1 + i, x2 - i, y2 - i, z2 - i, r, g, b, a);
            }
        }

        matrices.popPose();
        // draw(ToolList.mc, pipeline);
    }

    public void renderTrace(WorldRenderContext context, RenderPipeline pipeline, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        PoseStack matrices = context.matrices();
        Vec3 camera = context.worldState().cameraRenderState.pos;

        assert matrices != null;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        if (buffer == null) {
            buffer = new BufferBuilder(allocator, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
        }

        for (float i = -0.0005f; i <= 0.0005f; i += 0.0005f) {
            ShapeRenderer.renderVector(matrices, buffer, new Vector3f(x1 - i, y1, z1 - i), new Vec3(x2 - i, y2, z2 - i), new Color(r, g, b, a).getRGB());
            ShapeRenderer.renderVector(matrices, buffer, new Vector3f(x1 - i, y1, z1 + i), new Vec3(x2 - i, y2, z2 + i), new Color(r, g, b, a).getRGB());
        }
        matrices.popPose();
    }
    // :::custom-pipelines:extraction-phase

    // :::custom-pipelines:drawing-phase
    public void draw(Minecraft client, @SuppressWarnings("SameParameterValue") RenderPipeline pipeline) {
        // Build the buffer
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
        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

        // Initialize or resize the vertex buffer as needed
        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }

            vertexBuffer = new MappableRingBuffer(() -> "SkyDiao render pipeline", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
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
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, new Vector3f(), RenderSystem.getTextureMatrix(), 1f);
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
    }
    // :::custom-pipelines:clean-up
}
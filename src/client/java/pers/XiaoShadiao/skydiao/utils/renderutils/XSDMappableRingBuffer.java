package pers.XiaoShadiao.skydiao.utils.renderutils;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.Supplier;

public class XSDMappableRingBuffer implements AutoCloseable {
	private final int bufferCount;
	private final GpuBuffer[] buffers;
	private final GpuFence[] fences;
	private final int size;
	private int current = 0;

	public XSDMappableRingBuffer(final Supplier<String> label, @GpuBuffer.Usage final int usage, final int size, int bufferCount) {
		this.bufferCount = bufferCount;
		this.buffers = new GpuBuffer[bufferCount];
		this.fences = new GpuFence[bufferCount];
		GpuDevice device = RenderSystem.getDevice();
		if ((usage & 1) == 0 && (usage & 2) == 0) {
			throw new IllegalArgumentException("MappableRingBuffer requires at least one of USAGE_MAP_READ or USAGE_MAP_WRITE");
		} else {
			for (int i = 0; i < bufferCount; i++) {
				int finalI = i;
				this.buffers[i] = device.createBuffer(() -> (String)label.get() + " #" + finalI, usage, size);
				this.fences[i] = null;
			}

			this.size = size;
		}
	}

	public int size() {
		return this.size;
	}

	public GpuBuffer currentBuffer() {
		GpuFence fence = this.fences[this.current];
		if (fence != null) {
			fence.awaitCompletion(Long.MAX_VALUE);
			fence.close();
			this.fences[this.current] = null;
		}

		return this.buffers[this.current];
	}

	public void rotate() {
		if (this.fences[this.current] != null) {
			this.fences[this.current].close();
		}

		this.fences[this.current] = RenderSystem.getDevice().createCommandEncoder().createFence();
		this.current = (this.current + 1) % bufferCount;
	}

	public void close() {
		for (int i = 0; i < bufferCount; i++) {
			this.buffers[i].close();
			if (this.fences[i] != null) {
				this.fences[i].close();
			}
		}
	}
}

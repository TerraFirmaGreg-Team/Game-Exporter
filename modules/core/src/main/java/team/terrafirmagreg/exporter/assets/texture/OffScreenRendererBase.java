package team.terrafirmagreg.exporter.assets.texture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import team.terrafirmagreg.exporter.assets.atlas.ArgbImage;
import team.terrafirmagreg.exporter.assets.atlas.CaptureMetrics;
import team.terrafirmagreg.exporter.util.RunMetrics;

public abstract class OffScreenRendererBase implements AutoCloseable {
    private final IntBuffer pixels;
    private final int width;
    private final int height;

    protected OffScreenRendererBase(int width, int height) {
        this.width = width;
        this.height = height;
        pixels = ByteBuffer.allocateDirect(width * height * Integer.BYTES)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int[] captureArgb(Runnable draw) throws Exception {
        return captureArgbWithMetrics(draw).argb();
    }

    public CaptureMetrics captureArgbWithMetrics(Runnable draw) throws Exception {
        return captureArgbWithMetrics(width, height, draw);
    }

    public CaptureMetrics captureArgbWithMetrics(int readWidth, int readHeight, Runnable draw) throws Exception {
        return captureRegion(readWidth, readHeight, 0, 0, readWidth, readHeight, draw);
    }

    public final CaptureMetrics captureRegion(
            int viewportWidth, int viewportHeight, int readX, int readY, int readWidth, int readHeight, Runnable draw)
            throws Exception {
        if (readWidth <= 0 || readHeight <= 0 || readWidth > width || readHeight > height) {
            throw new IllegalArgumentException(
                    "readback " + readWidth + "x" + readHeight + " exceeds " + width + "x" + height);
        }
        RunMetrics metrics = new RunMetrics();
        metrics.timed("render", () -> {
            bindWrite();
            viewport(0, 0, viewportWidth, viewportHeight);
            clearBuffers();
            draw.run();
        });
        int[] argb = metrics.timed("readback", () -> {
            pixels.clear();
            readPixels(readX, readY, readWidth, readHeight, pixels);
            unbindWrite();
            int[] raw = new int[readWidth * readHeight];
            pixels.get(raw);
            return ArgbImage.bgraRevToArgb(raw, readWidth, readHeight, true);
        });
        return new CaptureMetrics(
                argb, metrics.timings().get("render"), metrics.timings().get("readback"));
    }

    @Override
    public void close() {
        destroyTarget();
    }

    protected abstract void bindWrite();

    protected abstract void unbindWrite();

    protected abstract void viewport(int x, int y, int width, int height);

    protected abstract void clearBuffers();

    protected abstract void readPixels(int x, int y, int width, int height, IntBuffer into);

    protected abstract void destroyTarget();
}

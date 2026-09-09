package misanthropy.brute_force_culling_revived.instanced;

import misanthropy.brute_force_culling_revived.instanced.attribute.GLFloatVertex;
import misanthropy.brute_force_culling_revived.instanced.attribute.GLVertex;
import org.jetbrains.annotations.NotNull;

import java.nio.FloatBuffer;
import java.util.function.Consumer;

public class PixelVertexBuffer extends VertexAttrib {
    private static final int CAPACITY = 64;

    private final int componentStride;

    public PixelVertexBuffer(int index) {
        super(CAPACITY, GLFloatVertex.createF2(index, "Position"));

        int stride = 0;
        for (GLVertex vertex : vertices) {
            stride += vertex.size();
        }
        this.componentStride = stride;
    }

    @Override
    public void addAttrib(Consumer<FloatBuffer> bufferConsumer) {}

    @Override
    public void init(@NotNull Consumer<FloatBuffer> bufferConsumer) {
        bufferConsumer.accept(this.buffer);
        setVertexCount(this.buffer.limit() / componentStride);
    }

    @Override
    public boolean needUpdate() {
        return false;
    }
}

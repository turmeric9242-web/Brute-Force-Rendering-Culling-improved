package misanthropy.brute_force_culling_revived.instanced;

import misanthropy.brute_force_culling_revived.instanced.attribute.GLFloatVertex;
import org.jetbrains.annotations.NotNull;

import java.nio.FloatBuffer;
import java.util.function.Consumer;

public class EntityUpdateVertex extends VertexAttrib {
    private static final int CAPACITY = 2097152;

    public EntityUpdateVertex(int index) {
        super(CAPACITY,
                GLFloatVertex.createF1(index,     "index"),
                GLFloatVertex.createF2(index + 1, "Size"),
                GLFloatVertex.createF3(index + 2, "EntityCenter")
        );
    }

    @Override
    public void addAttrib(@NotNull Consumer<FloatBuffer> bufferConsumer) {
        bufferConsumer.accept(this.buffer);
    }

    @Override
    public void init(Consumer<FloatBuffer> bufferConsumer) {}

    @Override
    public boolean needUpdate() {
        return true;
    }
}

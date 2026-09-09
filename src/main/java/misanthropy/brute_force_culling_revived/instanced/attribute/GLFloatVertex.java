package misanthropy.brute_force_culling_revived.instanced.attribute;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.jetbrains.annotations.NotNull;

public class GLFloatVertex extends GLVertex {

    public GLFloatVertex(int index, String name, int size) {
        super(index, name, size);
    }

    public static @NotNull GLFloatVertex createF1(int index, String name) {
        return new GLFloatVertex(index, name, 1);
    }

    public static @NotNull GLFloatVertex createF2(int index, String name) {
        return new GLFloatVertex(index, name, 2);
    }

    public static @NotNull GLFloatVertex createF3(int index, String name) {
        return new GLFloatVertex(index, name, 3);
    }

    @Override
    public VertexFormatElement.@NotNull Type elementType() {
        return VertexFormatElement.Type.FLOAT;
    }
}

package misanthropy.brute_force_culling_revived.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractOptionSliderButton;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class NeatSliderButton extends AbstractOptionSliderButton {
    private final @NotNull Supplier<Component> nameFunc;
    private final @NotNull Consumer<Double> applyValue;
    private Supplier<Component> detailMessage;
    private Supplier<Integer> textWidth;

    protected NeatSliderButton(int x, int y, int w, int h, @NotNull Supplier<Double> getter, @NotNull Function<Double, Double> setter, @NotNull Function<Double, String> display, @NotNull Supplier<MutableComponent> name) {
        super(Minecraft.getInstance().options, x, y, w, h, getter.get());
        this.nameFunc = () -> name.get().append(": ").append(Component.literal(display.apply(this.value)));
        this.applyValue = (val) -> this.value = setter.apply(val);
        updateMessage();
    }

    public void setDetailMessage(Supplier<Component> detailMessage) {
        this.detailMessage = detailMessage;
    }

    public void setTextWidthGetter(Supplier<Integer> widthGetter) {
        this.textWidth = widthGetter;
    }

    @Override
    public void updateMessage() {
        this.setMessage(nameFunc.get());
    }

    @Override
    protected void applyValue() {
        applyValue.accept(this.value);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (this.textWidth != null) {
            this.width = this.textWidth.get();
        }
        this.setX(minecraft.getWindow().getGuiScaledWidth() / 2 - width / 2);

        Font font = minecraft.font;
        int textColor = this.active ? 0xFFFFFF : 0xA0A0A0;

        guiGraphics.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float color = this.isHovered() ? 1.0f : 0.8f;
        buffer.vertex(this.getX(), this.getY() + height, 0.0D).color(color, color, color, 1.0f).endVertex();
        buffer.vertex(this.getX() + width, this.getY() + height, 0.0D).color(color, color, color, 1.0f).endVertex();
        buffer.vertex(this.getX() + width, this.getY(), 0.0D).color(color, color, color, 1.0f).endVertex();
        buffer.vertex(this.getX(), this.getY(), 0.0D).color(color, color, color, 1.0f).endVertex();

        float borderColor = 0.7f;
        buffer.vertex(this.getX() - 1, this.getY() + height + 1, 0.0D).color(borderColor, borderColor, borderColor, 1.0f).endVertex();
        buffer.vertex(this.getX() + width + 1, this.getY() + height + 1, 0.0D).color(borderColor, borderColor, borderColor, 1.0f).endVertex();
        buffer.vertex(this.getX() + width + 1, this.getY() - 1, 0.0D).color(borderColor, borderColor, borderColor, 1.0f).endVertex();
        buffer.vertex(this.getX() - 1, this.getY() - 1, 0.0D).color(borderColor, borderColor, borderColor, 1.0f).endVertex();

        float thumbColor = 1.0f;
        int thumbX = this.getX() + (int) (this.value * (double) (this.width - 8));
        buffer.vertex(thumbX, this.getY() + height, 0.0D).color(thumbColor, thumbColor, thumbColor, 1.0f).endVertex();
        buffer.vertex(thumbX + 8, this.getY() + height, 0.0D).color(thumbColor, thumbColor, thumbColor, 1.0f).endVertex();
        buffer.vertex(thumbX + 8, this.getY(), 0.0D).color(thumbColor, thumbColor, thumbColor, 1.0f).endVertex();
        buffer.vertex(thumbX, this.getY(), 0.0D).color(thumbColor, thumbColor, thumbColor, 1.0f).endVertex();

        BufferUploader.drawWithShader(buffer.end());

        guiGraphics.flush();
        RenderSystem.defaultBlendFunc();

        guiGraphics.drawCenteredString(font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, textColor | 0xFF000000);
        RenderSystem.enableDepthTest();
    }

    public @Nullable Component getDetails() {
        return isHovered && detailMessage != null ? detailMessage.get() : null;
    }
}
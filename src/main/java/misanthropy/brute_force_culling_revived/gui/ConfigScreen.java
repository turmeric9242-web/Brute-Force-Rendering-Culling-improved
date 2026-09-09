package misanthropy.brute_force_culling_revived.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import misanthropy.brute_force_culling_revived.api.Config;
import misanthropy.brute_force_culling_revived.api.CullingStateManager;
import misanthropy.brute_force_culling_revived.api.ModLoader;
import misanthropy.brute_force_culling_revived.util.Benchmark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ConfigScreen extends Screen {
    private boolean release = false;
    private final int heightScale;
    private int textWidth;

    private GuiEventListener lastHoveredChild = null;
    private final List<TooltipLine> cachedTooltipLines = new ArrayList<>();
    private int cachedTooltipTotalHeight = 0;
    private static final int MAX_TOOLTIP_WIDTH = 202;

    public ConfigScreen(@NotNull Component titleIn) {
        super(titleIn);
        this.heightScale = (int) (Minecraft.getInstance().font.lineHeight * 2f + 1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private float getU(int x, int windowWidth) {
        return (float) x / windowWidth;
    }

    private float getV(int y, int windowHeight) {
        return 1.0f - ((float) y / windowHeight);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        int windowWidth = mc.getWindow().getGuiScaledWidth();
        int windowHeight = mc.getWindow().getGuiScaledHeight();

        int centerX = windowWidth / 2;
        int widthScale = textWidth / 2 + 15;
        int right = centerX - widthScale;
        int left = centerX + widthScale;
        int bottom = (int) (windowHeight * 0.8) + 20;
        int top = bottom - heightScale * children().size() - 10;

        float bgColor = 1.0f;
        float bgAlpha = 0.3f;

        guiGraphics.flush();
        RenderSystem.disableDepthTest();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();

        if (CullingStateManager.REMOVE_COLOR_SHADER != null) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.1f);
            CullingStateManager.useShader(CullingStateManager.REMOVE_COLOR_SHADER);

            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);

            bufferbuilder.vertex(right - 1, bottom + 1, 0.0D).color(bgColor, bgColor, bgColor, bgAlpha).uv(getU(right - 1, windowWidth), getV(bottom + 1, windowHeight)).endVertex();
            bufferbuilder.vertex(left + 1, bottom + 1, 0.0D).color(bgColor, bgColor, bgColor, bgAlpha).uv(getU(left + 1, windowWidth), getV(bottom + 1, windowHeight)).endVertex();
            bufferbuilder.vertex(left + 1, top - 1, 0.0D).color(bgColor, bgColor, bgColor, bgAlpha).uv(getU(left + 1, windowWidth), getV(top - 1, windowHeight)).endVertex();
            bufferbuilder.vertex(right - 1, top - 1, 0.0D).color(bgColor, bgColor, bgColor, bgAlpha).uv(getU(right - 1, windowWidth), getV(top - 1, windowHeight)).endVertex();

            RenderSystem.setShaderTexture(0, mc.getMainRenderTarget().getColorTextureId());
            BufferUploader.drawWithShader(bufferbuilder.end());
        }

        bgAlpha = 1.0f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex(right, bottom, 0.0D).color(bgColor, bgColor, bgColor, bgAlpha).endVertex();
        bufferbuilder.vertex(left, bottom, 0.0D).color(bgColor, bgColor, bgColor, bgAlpha).endVertex();
        bufferbuilder.vertex(left, top, 0.0D).color(bgColor, bgColor, bgColor, bgAlpha).endVertex();
        bufferbuilder.vertex(right, top, 0.0D).color(bgColor, bgColor, bgColor, bgAlpha).endVertex();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO);
        BufferUploader.drawWithShader(bufferbuilder.end());

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0f);
        RenderSystem.enableDepthTest();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (this.minecraft != null && (this.minecraft.options.keyInventory.matches(key, scan) || this.minecraft.options.keyPlayerList.matches(key, scan))) {
            this.onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (ModLoader.CONFIG_KEY.matches(key, scan)) {
            if (release) {
                this.onClose();
                return true;
            } else {
                release = true;
            }
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    protected void init() {
        if (this.minecraft == null || this.minecraft.player == null) {
            onClose();
            return;
        }

        this.textWidth = 0;

        if (CullingStateManager.DEBUG > 0) {
            addConfigButton(() -> CullingStateManager.checkCulling, (b) -> CullingStateManager.checkCulling = b, () -> Component.translatable("brute_force_culling_revived.check_culling"))
                    .setDetailMessage(() -> Component.translatable("brute_force_culling_revived.detail.debug"));

            addConfigButton(() -> CullingStateManager.checkTexture, (b) -> CullingStateManager.checkTexture = b, () -> Component.translatable("brute_force_culling_revived.check_texture"))
                    .setDetailMessage(() -> Component.translatable("brute_force_culling_revived.detail.check_texture"));

            addConfigButton(Benchmark::isActive, (b) -> {
                Benchmark.toggle(b);
                if (b) onClose();
            }, () -> Component.translatable("brute_force_culling_revived.benchmark"))
                    .setDetailMessage(() -> Component.translatable("brute_force_culling_revived.detail.benchmark"));
        }

        addConfigButton(Config::getSampling, (value) -> {
            double format = Mth.floor(value * 20) * 0.05;
            format = Double.parseDouble(String.format("%.2f", format));
            Config.setSampling(format);
            return format;
        }, (value) -> Mth.floor(value * 100) + "%", () -> Component.translatable("brute_force_culling_revived.sampler"))
                .setDetailMessage(() -> Component.translatable("brute_force_culling_revived.detail.sampler"));

        addConfigButton(() -> Config.getDepthUpdateDelay() / 10d, (value) -> {
            int format = Mth.floor(value * 10);
            if (format > 0) format -= Config.getShaderDynamicDelay();
            Config.setDepthUpdateDelay(format);
            format += Config.getShaderDynamicDelay();
            return format * 0.1;
        }, (value) -> String.valueOf(Mth.floor(value * 10)), () -> Component.translatable("brute_force_culling_revived.culling_map_update_delay"))
                .setDetailMessage(() -> Component.translatable("brute_force_culling_revived.detail.culling_map_update_delay"));

        addConfigButton(Config::getAutoDisableAsync, Config::setAutoDisableAsync, () -> Component.translatable("brute_force_culling_revived.auto_shader_async"))
                .setDetailMessage(() -> Component.translatable("brute_force_culling_revived.detail.auto_shader_async"));

        addConfigButton(() -> ModLoader.hasSodium() && !ModLoader.hasNvidium(), Config::getAsyncChunkRebuild, Config::setAsyncChunkRebuild, () -> Component.translatable("brute_force_culling_revived.async"))
                .setDetailMessage(() -> {
                    if (ModLoader.hasNvidium()) return Component.translatable("brute_force_culling_revived.detail.nvidium");
                    if (!ModLoader.hasSodium()) return Component.translatable("brute_force_culling_revived.detail.sodium");
                    return Component.translatable("brute_force_culling_revived.detail.async");
                });

        addConfigButton(Config::getCullChunk, Config::setCullChunk, () -> Component.translatable("brute_force_culling_revived.cull_chunk"))
                .setDetailMessage(() -> Component.translatable("brute_force_culling_revived.detail.cull_chunk"));

        addConfigButton(Config::getCullBlockEntity, Config::setCullBlockEntity, () -> Component.translatable("brute_force_culling_revived.cull_block_entity"))
                .setDetailMessage(() -> CullingStateManager.gl33() ? Component.translatable("brute_force_culling_revived.detail.cull_block_entity") : Component.translatable("brute_force_culling_revived.detail.gl33"));

        addConfigButton(Config::getCullEntity, Config::setCullEntity, () -> Component.translatable("brute_force_culling_revived.cull_entity"))
                .setDetailMessage(() -> CullingStateManager.gl33() ? Component.translatable("brute_force_culling_revived.detail.cull_entity") : Component.translatable("brute_force_culling_revived.detail.gl33"));

        addConfigButton(Config::getCullEntity, Config::getTickCulling, Config::setTickCulling, () -> Component.translatable("brute_force_culling_revived.tick_culling"))
                .setDetailMessage(() -> Component.translatable("brute_force_culling_revived.detail.tick_culling"));

        super.init();
    }

    public @NotNull NeatButton addConfigButton(Supplier<Boolean> getter, @NotNull Consumer<Boolean> setter, @NotNull Supplier<Component> displayText) {
        return addConfigButton(() -> true, getter, setter, displayText);
    }

    public @NotNull NeatButton addConfigButton(Supplier<Boolean> enable, Supplier<Boolean> getter, @NotNull Consumer<Boolean> setter, @NotNull Supplier<Component> displayText) {
        int w = 150;
        int x = this.width / 2 - w / 2;
        NeatButton button = new NeatButton(x, (int) ((height * 0.8) - heightScale * children().size()), w, 14, enable, getter, setter, displayText);
        this.addRenderableWidget(button);
        this.textWidth = Math.max(Math.max(w, font.width(displayText.get()) + 40), this.textWidth);
        button.setTextWidthGetter(() -> this.textWidth);
        return button;
    }

    public @NotNull NeatSliderButton addConfigButton(@NotNull Supplier<Double> getter, @NotNull Function<Double, Double> setter, @NotNull Function<Double, String> display, @NotNull Supplier<MutableComponent> displayText) {
        int w = 150;
        int x = this.width / 2 - w / 2;
        NeatSliderButton button = new NeatSliderButton(x, (int) ((height * 0.8) - heightScale * children().size()), w, 14, getter, setter, display, displayText);
        this.addRenderableWidget(button);
        this.textWidth = Math.max(Math.max(w, font.width(displayText.get()) + 40), this.textWidth);
        button.setTextWidthGetter(() -> this.textWidth);
        return button;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        AbstractWidget hovered = null;
        for (GuiEventListener child : children()) {
            if (child instanceof AbstractWidget widget && widget.isMouseOver(mouseX, mouseY)) {
                hovered = widget;
                break;
            }
        }

        if (hovered != null) {
            Component details = null;
            if (hovered instanceof NeatButton b) details = b.getDetails();
            else if (hovered instanceof NeatSliderButton b) details = b.getDetails();

            if (details != null) {
                updateTooltipCache(hovered, details);
                renderCachedDetails(guiGraphics);
            }
        } else {
            lastHoveredChild = null;
        }
    }

    private void updateTooltipCache(GuiEventListener child, Component details) {
        if (lastHoveredChild == child) return;

        lastHoveredChild = child;
        cachedTooltipLines.clear();
        cachedTooltipTotalHeight = 0;

        String[] parts = details.getString().split("\\n");
        int maxWidth = Math.min(this.width - 20, MAX_TOOLTIP_WIDTH);

        for (String part : parts) {
            boolean isWarning = part.contains("warn:");
            String cleaned = part.replace("warn:", "");
            List<FormattedCharSequence> lines = font.split(Component.literal(cleaned), maxWidth);

            if (lines.isEmpty()) {
                cachedTooltipTotalHeight += font.lineHeight / 2;
            } else {
                for (FormattedCharSequence line : lines) {
                    cachedTooltipLines.add(new TooltipLine(line, isWarning));
                }
                cachedTooltipTotalHeight += lines.size() * font.lineHeight + font.lineHeight / 4;
            }
        }
    }

    private void renderCachedDetails(@NotNull GuiGraphics guiGraphics) {
        if (cachedTooltipLines.isEmpty()) return;

        int maxWidth = Math.min(this.width - 20, MAX_TOOLTIP_WIDTH);
        int x = this.width / 2 - maxWidth / 2;
        int y = 4;

        guiGraphics.fill(x - 2, y - 2, x + maxWidth + 2, y + cachedTooltipTotalHeight + 2, 0xB0000000);

        int currentY = y;
        for (TooltipLine line : cachedTooltipLines) {
            guiGraphics.drawString(font, line.text, x, currentY, line.isWarning ? 0xFFFF5555 : 0xFFFFFFFF, false);
            currentY += font.lineHeight;
        }
    }

    private record TooltipLine(FormattedCharSequence text, boolean isWarning) {}
}
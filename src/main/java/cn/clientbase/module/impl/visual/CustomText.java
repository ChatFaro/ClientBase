package cn.clientbase.module.impl.visual;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.RenderSkiaEvent;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.module.value.impl.StringValue;
import cn.clientbase.ui.font.base.SkiaFont;
import cn.clientbase.ui.hud.Drag;
import cn.clientbase.util.render.ColorUtil;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;

@Getter
public class CustomText extends Drag {
    private final StringValue text = new StringValue("Text", "DSJ#8963");
    private final NumberValue size = new NumberValue("Size", 16, 8, 48, 1);

    public CustomText() {
        super("CustomText");
        setDescription("Draggable custom text rendered with the Skia font.");
        percentX = 0.05f;
        percentY = 0.20f;
    }

    private SkiaFont font() {
        return instance.getFontManager().getMediumFont(size.getValue().intValue());
    }

    /**
     * Runs in the Render2DEvent pass (before Skia). We don't draw text here — Skia owns the
     * glyphs — but we keep the drag hitbox sized to the current text so dragging stays accurate.
     */
    @Override
    public void render(DrawContext context) {
        SkiaFont font = font();
        String value = text.getValue();
        this.width = Math.max(4, font.getStringWidth(value.isEmpty() ? " " : value));
        this.height = font.getFontHeight();
    }

    @EventTarget
    public void onRenderSkia(RenderSkiaEvent event) {
        if (mc.player == null || mc.world == null) return;

        String value = text.getValue();
        if (value.isEmpty()) return;

        final HUD hud = getModule(HUD.class);
        int color = hud != null ? hud.getColor(1) : ColorUtil.applyAlpha(java.awt.Color.WHITE, 1f).getRGB();

        // Drag positions are in scaled GUI coordinates; the Skia canvas is scaled by the same
        // factor in SkiaManager, so renderX/renderY map directly onto the canvas.
        font().drawShadowString(event.getCanvas(), value, renderX, renderY, color);
    }
}

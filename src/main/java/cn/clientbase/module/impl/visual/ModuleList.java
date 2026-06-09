package cn.clientbase.module.impl.visual;

import cn.clientbase.Client;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.value.impl.BoolValue;
import cn.clientbase.ui.hud.Drag;
import cn.clientbase.util.render.ColorUtil;
import cn.clientbase.util.render.FontUtil;
import cn.clientbase.util.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.Comparator;
import java.util.List;

public final class ModuleList extends Drag {
    private final BoolValue important = new BoolValue("Important", false);
    private final BoolValue suffix = new BoolValue("Suffix", true);
    private final BoolValue background = new BoolValue("Background", true);

    public ModuleList() {
        super("ModuleList");
        setDescription("Module list");
        percentX = 0.95F;
        percentY = 0.05F;
    }

    @Override
    public boolean isRightAnchored() {
        return percentX > 0.5F;
    }

    @Override
    public void render(DrawContext context) {
        if (mc.textRenderer == null) return;

        final HUD hud = getModule(HUD.class);
        if (hud.isExhibitionStyle()) {
            renderExhibition(context, hud);
            return;
        }

        final List<Module> modules = Client.instance.getModuleManager().getModuleMap().values().stream()
                .filter(m -> !m.isHidden() && (m.isEnabled() || m.getAnimation().getValue() > 0))
                .filter(m -> !important.getValue() || (m.getCategory() != Category.Client && m.getCategory() != Category.Visual))
                .sorted(Comparator.comparingInt((Module m) -> (int) FontUtil.getStringWidth(m.getName() + (suffix.getValue() && !m.getSuffix().isEmpty() ? " " + m.getSuffix() : ""))).reversed().thenComparing(Module::getName))
                .toList();

        if (modules.isEmpty()) return;

        float maxWidth = 0;
        for (final Module module : modules) {
            final String displayName = module.getName() + (suffix.getValue() && !module.getSuffix().isEmpty() ? " " + module.getSuffix() : "");
            maxWidth = Math.max(maxWidth, FontUtil.getStringWidth(displayName));
        }
        this.width = maxWidth + 4;

        float offsetY = renderY;
        int index = 0;

        for (final Module module : modules) {
            module.getAnimation().run(module.isEnabled() ? 1 : 0);
            final float alpha = (float) module.getAnimation().getValue();
            if (alpha <= 0.02f) continue;

            final boolean hasSuffix = suffix.getValue() && !module.getSuffix().isEmpty();
            final String moduleName = module.getName();
            final String suffixName = hasSuffix ? " " + module.getSuffix() : "";

            final float textWidth = FontUtil.getStringWidth(moduleName + suffixName);
            final float offset = (1 - alpha) * (this.width + 10);
            final float x = isRightAnchored() ? (renderX - textWidth + offset) : (renderX - offset);

            if (background.getValue()) {
                RenderUtil.drawRect(context, x - 2, offsetY, textWidth + 4, FontUtil.getHeight(), ColorUtil.applyAlpha(new Color(0, 0, 0, 120), alpha).getRGB());
            }

            FontUtil.drawStringWithShadow(context, moduleName + Formatting.GRAY + suffixName, x, offsetY, ColorUtil.applyAlpha(hud.getColor(index++), alpha));
            offsetY += FontUtil.getHeight() * alpha;
        }

        this.height = Math.max(FontUtil.getHeight(), offsetY - renderY);
    }

    private void renderExhibition(DrawContext context, HUD hud) {
        final List<Module> modules = Client.instance.getModuleManager().getModuleMap().values().stream()
                .filter(m -> !m.isHidden() && (m.isEnabled() || m.getAnimation().getValue() > 0))
                .filter(m -> !important.getValue() || (m.getCategory() != Category.Client && m.getCategory() != Category.Visual))
                .sorted(Comparator.comparingInt((Module m) -> (int) FontUtil.getStringWidth(getDisplayName(m))).reversed().thenComparing(Module::getName))
                .toList();

        if (modules.isEmpty()) return;

        float maxWidth = 0;
        for (Module module : modules) {
            maxWidth = Math.max(maxWidth, FontUtil.getStringWidth(getDisplayName(module)));
        }
        this.width = maxWidth + 2;

        float y = 20;
        float h = (mc.player != null ? mc.player.age * 3.5f : System.currentTimeMillis() / 18f) % 255f;
        int index = 0;
        for (Module module : modules) {
            module.getAnimation().run(module.isEnabled() ? 1 : 0);
            float alpha = (float) module.getAnimation().getValue();
            if (alpha <= 0.02f) continue;

            String name = getDisplayName(module);
            float x = 4 - (1 - alpha) * 8;
            int color = MathHelper.hsvToRgb(((h + index * 9f) / 255f) % 1f, 0.55f, 0.9f);
            int argb = ColorUtil.applyAlpha(new Color(color), alpha).getRGB();
            FontUtil.drawStringWithShadow(context, name, x, y, argb);
            y += 9 * alpha;
            index++;
        }

        this.height = Math.max(FontUtil.getHeight(), y);
    }

    private String getDisplayName(Module module) {
        return module.getName() + (suffix.getValue() && !module.getSuffix().isEmpty() ? " " + module.getSuffix() : "");
    }
}

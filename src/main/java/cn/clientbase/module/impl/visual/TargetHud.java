package cn.clientbase.module.impl.visual;

import cn.clientbase.module.impl.combat.KillAura;
import cn.clientbase.ui.hud.Drag;
import cn.clientbase.util.render.ColorUtil;
import cn.clientbase.util.render.FontUtil;
import cn.clientbase.util.render.RenderUtil;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TargetHud extends Drag {
    public TargetHud() {
        super("TargetHud");
        setDescription("Exhibition style target information panel");
        setEnabled(true);
        percentX = 0.42f;
        percentY = 0.56f;
        width = 124;
        height = 45;
    }

    @Override
    public void render(DrawContext context) {
        HUD hud = getModule(HUD.class);
        if (!hud.isExhibitionStyle() || mc.player == null) return;

        LivingEntity target = getTarget();
        if (target == null) return;

        String name = target.getName().getString();
        float renderWidth = Math.max(120, 43 + FontUtil.getStringWidth(name));
        this.width = renderWidth + 5;
        this.height = 45;

        float x = renderX;
        float y = renderY;
        drawBordered(context, x - 2.5f, y - 2.5f, renderWidth + 5, 45, 0xFF0A0A0A, 0xFF3C3C3C);
        drawBordered(context, x - 1.5f, y - 1.5f, renderWidth + 3, 43, 0xFF282828, 0xFF3C3C3C);
        drawBordered(context, x, y, renderWidth, 40, 0xFF161616, 0xFF3C3C3C);

        drawBordered(context, x + 2, y + 2, 36, 36, 0x00000000, 0xFF0A0A0A);
        drawBordered(context, x + 2.5f, y + 2.5f, 35, 35, 0xFF111111, 0xFF303030);
        drawTargetModel(context, target, x + 3, y + 3);

        FontUtil.drawStringWithShadow(context, name, x + 39, y + 3, 0xFFFFFFFF);

        float health = Math.max(0, target.getHealth());
        float maxHealth = Math.max(1, target.getMaxHealth());
        float progress = Math.min(1, health / maxHealth);
        float barWidth = Math.max(60, Math.min(80, FontUtil.getStringWidth(name) + 8));
        int healthColor = blendHealth(progress).brighter().getRGB();
        drawBordered(context, x + 39, y + 12, barWidth + 2, 4, 0xFF000000, 0xFF000000);
        RenderUtil.drawRect(context, x + 40, y + 13, barWidth, 2, new Color(healthColor, true).darker().getRGB());
        RenderUtil.drawRect(context, x + 40, y + 13, barWidth * progress, 2, healthColor);
        for (int i = 1; i < 10; i++) {
            float px = x + 40 + (barWidth / 10f) * i;
            RenderUtil.drawRect(context, px, y + 12, 0.5f, 4, 0xFF000000);
        }

        String info = "HP: " + (int) health + " | Dist: " + (int) mc.player.distanceTo(target);
        FontUtil.drawStringWithShadow(context, info, x + 39, y + 18, 0xFFFFFFFF);

        renderItems(context, target, x + 39, y + 23, renderWidth);
    }

    private void drawTargetModel(DrawContext context, LivingEntity target, float x, float y) {
        int left = Math.round(x);
        int top = Math.round(y);
        int right = left + 34;
        int bottom = top + 34;
        float largestSize = Math.max(target.getHeight(), target.getWidth());
        int scale = Math.max(10, Math.round(16.0f / Math.max(largestSize / 1.8f, 1.0f)));
        InventoryScreen.drawEntity(
                context,
                left, top, right, bottom,
                scale,
                0.0f,
                left + 17.0f,
                top + 17.0f,
                target
        );
    }

    private LivingEntity getTarget() {
        KillAura aura = getModule(KillAura.class);
        if (aura != null && aura.isEnabled() && aura.getTarget() != null) return aura.getTarget();
        return mc.currentScreen != null ? mc.player : null;
    }

    private void renderItems(DrawContext context, LivingEntity target, float x, float y, float renderWidth) {
        List<ItemStack> items = getItems(target);
        int itemX = Math.round(x);
        int itemY = Math.round(y);
        int right = Math.round(renderX + renderWidth - 2);
        for (ItemStack item : items) {
            if (itemX + 16 > right) break;
            context.drawItem(item, itemX, itemY);
            context.drawStackOverlay(mc.textRenderer, item, itemX, itemY);
            itemX += 16;
        }
    }

    private List<ItemStack> getItems(LivingEntity target) {
        List<ItemStack> items = new ArrayList<>();
        if (target instanceof PlayerEntity player) {
            List<ItemStack> armor = new ArrayList<>(player.getInventory().armor);
            Collections.reverse(armor);
            for (ItemStack stack : armor) {
                if (!stack.isEmpty()) items.add(stack);
            }
            ItemStack hand = player.getMainHandStack();
            if (!hand.isEmpty()) items.add(hand);
        }
        return items;
    }

    private Color blendHealth(float progress) {
        Color low = Color.RED;
        Color mid = Color.YELLOW;
        Color high = Color.GREEN;
        if (progress < 0.5f) {
            return ColorUtil.blend(low, mid, progress / 0.5f);
        }
        return ColorUtil.blend(mid, high, (progress - 0.5f) / 0.5f);
    }

    private void drawBordered(DrawContext context, float x, float y, float width, float height, int fill, int border) {
        RenderUtil.drawRect(context, x, y, width, height, border);
        RenderUtil.drawRect(context, x + 0.5f, y + 0.5f, width - 1f, height - 1f, fill);
    }
}

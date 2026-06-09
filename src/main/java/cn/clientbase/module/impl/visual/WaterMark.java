package cn.clientbase.module.impl.visual;

import cn.clientbase.Client;
import cn.clientbase.ui.hud.Drag;
import cn.clientbase.util.render.FontUtil;
import net.minecraft.client.gui.DrawContext;

public class WaterMark extends Drag {
    public WaterMark() {
        super("WaterMark");
        setDescription("Client WaterMark");
        this.percentX = 0.1f;
        this.percentY = 0.1f;
    }

    @Override
    public void render(DrawContext context) {
        if (mc.player == null) return;

        HUD hud = getModule(HUD.class);
        if (hud.isExhibitionStyle()) {
            renderExhibition(context, hud);
            return;
        }

        this.width = FontUtil.getStringWidth(Client.name) + 8;
        this.height = FontUtil.getHeight();
        FontUtil.drawStringWithShadow(context, Client.name, renderX + 4, renderY + 4, hud.getColor(1));
    }

    private void renderExhibition(DrawContext context, HUD hud) {
        String text = "Exhibition [1.8.x] [20.0]";
        String first = text.substring(0, 1);
        String rest = text.substring(1);
        float x = 2;
        float y = 2;
        this.width = FontUtil.getStringWidth(text) + 4;
        this.height = FontUtil.getHeight() + 2;
        FontUtil.drawStringWithShadow(context, first, x, y, hud.getColor(0));
        FontUtil.drawStringWithShadow(context, rest, x + FontUtil.getStringWidth(first), y, 0xE6FFFFFF);
    }
}

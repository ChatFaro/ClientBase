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
        this.width = FontUtil.getStringWidth(Client.name) + 8;
        this.height = FontUtil.getHeight();
        FontUtil.drawStringWithShadow(context, Client.name, renderX + 4, renderY + 4, hud.getColor(1));
    }
}
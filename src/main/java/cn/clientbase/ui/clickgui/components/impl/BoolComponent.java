package cn.clientbase.ui.clickgui.components.impl;
import cn.clientbase.Client;
import cn.clientbase.module.impl.visual.HUD;
import cn.clientbase.module.value.impl.BoolValue;
import cn.clientbase.ui.clickgui.components.ValueComponent;
import cn.clientbase.util.render.FontUtil;
import cn.clientbase.util.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;

public class BoolComponent extends ValueComponent {

    public BoolComponent(BoolValue value) {
        super(value);
    }

    @Override
    public float render(DrawContext context, int mouseX, int mouseY, float x, float y, float width) {
        RenderUtil.drawRect(context, x, y, width, 15, new Color(0, 0, 0, 120).getRGB());
        FontUtil.drawStringWithShadow(context, getValue().getName(), x + 6, y + 4, ((BoolValue)getValue()).getValue() ? Client.instance.getModuleManager().getModule(HUD.class).getColor() : 0xFFAAAAAA);
        return lastHeight = 15;
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button, float x, float y, float width) {
        if (button == 0 && hovered(mouseX, mouseY, x, y, width, 15)) {
            ((BoolValue)getValue()).toggle();
        }
    }
}
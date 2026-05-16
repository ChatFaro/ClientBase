package cn.clientbase.ui.clickgui.components.impl;

import cn.clientbase.Client;
import cn.clientbase.module.impl.visual.HUD;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.ui.clickgui.components.ValueComponent;
import cn.clientbase.util.render.FontUtil;
import cn.clientbase.util.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.awt.*;

public class NumberComponent extends ValueComponent {
    private boolean dragging;

    public NumberComponent(NumberValue value) {
        super(value);
    }

    @Override
    public float render(DrawContext context, int mouseX, int mouseY, float x, float y, float width) {
        NumberValue num = (NumberValue) getValue();
        if (dragging) {
            float percent = MathHelper.clamp((mouseX - x) / width, 0.0f, 1.0f);
            float val = num.getMin() + (num.getMax() - num.getMin()) * percent;
            num.setValue(Math.round(val / num.getInc()) * num.getInc());
        }

        RenderUtil.drawRect(context, x, y, width, 15, new Color(0, 0, 0, 120).getRGB());
        float percent = (num.getValue() - num.getMin()) / (num.getMax() - num.getMin());
        RenderUtil.drawGradientRect(context, x, y + 13, width * percent, 2, Client.instance.getModuleManager().getModule(HUD.class).getColor(), Client.instance.getModuleManager().getModule(HUD.class).getColor(4), false);
        String valStr = String.format("%.2f", num.getValue()).replace(".00", "");
        FontUtil.drawStringWithShadow(context, num.getName() + " " + valStr, x + 6, y + 3, 0xFFAAAAAA);
        return lastHeight = 15;
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button, float x, float y, float width) {
        if (button == 0 && hovered(mouseX, mouseY, x, y, width, 15)) {
            dragging = true;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        dragging = false;
    }
}
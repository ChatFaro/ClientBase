package cn.clientbase.ui.clickgui;

import cn.clientbase.Client;
import cn.clientbase.module.Module;
import cn.clientbase.module.impl.visual.HUD;
import cn.clientbase.module.value.Value;
import cn.clientbase.module.value.impl.*;
import cn.clientbase.ui.clickgui.components.*;
import cn.clientbase.ui.clickgui.components.impl.*;
import cn.clientbase.util.render.FontUtil;
import cn.clientbase.util.render.RenderUtil;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ModuleButton {
    @Getter
    private final Module module;
    @Getter
    private final List<ValueComponent> components = new ArrayList<>();
    @Getter
    private boolean expanded;
    @Getter
    private float height = 15;

    public ModuleButton(Module module) {
        this.module = module;
        for (Value value : module.getValues()) {
            if (value instanceof BoolValue bool) {
                components.add(new BoolComponent(bool));
            } else if (value instanceof ModeValue mode) {
                components.add(new ModeComponent(mode));
            } else if (value instanceof NumberValue num) {
                components.add(new NumberComponent(num));
            } else if (value instanceof ColorValue color) {
                components.add(new ColorComponent(color));
            } else if (value instanceof MultiBoolValue multi) {
                components.add(new MultiBoolComponent(multi));
            } else if (value instanceof StringValue str) {
                components.add(new StringComponent(str));
            }
        }
    }

    public float render(DrawContext context, int mouseX, int mouseY, float x, float y, float width) {
        float offsetY = 15;
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 15;
        RenderUtil.drawRect(context, x, y, width, 15,  new Color(0, 0, 0, hovered ? 100 : 80).getRGB());
        FontUtil.drawString(context, module.getName(), x + 4, y + 4, module.isEnabled() ? Client.instance.getModuleManager().getModule(HUD.class).getColor(1) : 0xFFAAAAAA, true);

        if (expanded) {
            for (ValueComponent comp : components) {
                if (!comp.getValue().isVisible()) continue;
                offsetY += comp.render(context, mouseX, mouseY, x, y + offsetY, width);
            }
        }

        height = offsetY;
        return height;
    }

    public void mouseClicked(int mouseX, int mouseY, int button, float x, float y, float width) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 15) {
            if (button == 0) {
                module.toggle();
            } else if (button == 1) {
                expanded = !expanded;
            }
        }

        if (expanded) {
            float offsetY = 15;
            for (ValueComponent comp : components) {
                if (!comp.getValue().isVisible()) continue;
                comp.mouseClicked(mouseX, mouseY, button, x, y + offsetY, width);
                offsetY += comp.getLastHeight();
            }
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (expanded) {
            components.forEach(c -> c.mouseReleased(mouseX, mouseY, button));
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!expanded) return false;
        for (ValueComponent comp : components) {
            if (comp.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!expanded) return false;
        for (ValueComponent comp : components) {
            if (comp.charTyped(chr, modifiers)) return true;
        }
        return false;
    }

    public float renderExhibition(DrawContext context, int mouseX, int mouseY, float x, float y, float width) {
        float offsetY = 16;
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 15;
        int fill = module.isEnabled() ? new Color(28, 28, 28, 235).getRGB() : new Color(18, 18, 18, hovered ? 235 : 210).getRGB();
        RenderUtil.drawRect(context, x, y, width, 15, fill);
        RenderUtil.drawRect(context, x, y, 1, 15, module.isEnabled() ? Client.instance.getModuleManager().getModule(HUD.class).getColor(2) : 0xFF303030);
        FontUtil.drawStringWithShadow(context, module.getName(), x + 5, y + 4, module.isEnabled() ? 0xFFFFFFFF : 0xFFB9B9B9);
        FontUtil.drawStringWithShadow(context, expanded ? "-" : "+", x + width - 8, y + 4, 0xFF888888);

        if (expanded) {
            for (ValueComponent comp : components) {
                if (!comp.getValue().isVisible()) continue;
                offsetY += comp.render(context, mouseX, mouseY, x + 3, y + offsetY, width - 6);
            }
        }

        height = offsetY;
        return height;
    }

    public void mouseClickedExhibition(int mouseX, int mouseY, int button, float x, float y, float width) {
        mouseClicked(mouseX, mouseY, button, x, y, width);
    }
}

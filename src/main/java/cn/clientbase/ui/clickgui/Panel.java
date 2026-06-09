package cn.clientbase.ui.clickgui;

import cn.clientbase.Client;
import cn.clientbase.module.Category;
import cn.clientbase.module.impl.visual.HUD;
import cn.clientbase.util.IMinecraft;
import cn.clientbase.util.render.FontUtil;
import cn.clientbase.util.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class Panel implements IMinecraft {
    private final List<ModuleButton> buttons = new ArrayList<>();
    private final Category category;
    private float x, y, dragX, dragY;
    private boolean dragging, open = true;
    private final float width = 105;

    public Panel(Category category, float x, float y) {
        this.category = category;
        this.x = x;
        this.y = y;

        instance.getModuleManager().getModuleMap().values().stream()
                .filter(m -> m.getCategory() == category)
                .forEach(m -> buttons.add(new ModuleButton(m)));
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        if (dragging) {
            x = mouseX - dragX;
            y = mouseY - dragY;
        }

        RenderUtil.drawGradientRect(context, x, y, width, 18, Client.instance.getModuleManager().getModule(HUD.class).getColor(), Client.instance.getModuleManager().getModule(HUD.class).getColor(4), true);
        FontUtil.drawString(context, category.getName(), x + width / 2f - FontUtil.getStringWidth(category.getName()) / 2f, y + 5, -1, true);

        if (open) {
            float offsetY = 18;
            for (ModuleButton button : buttons) {
                offsetY += button.render(context, mouseX, mouseY, x, y + offsetY, width);
            }
        }
    }

    public Category getCategory() {
        return category;
    }

    public List<ModuleButton> getButtons() {
        return buttons;
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 18) {
            if (button == 0) {
                dragging = true;
                dragX = mouseX - x;
                dragY = mouseY - y;
            } else if (button == 1) open = !open;
        }

        if (open) {
            float offsetY = 18;
            for (ModuleButton btn : buttons) {
                btn.mouseClicked(mouseX, mouseY, button, x, y + offsetY, width);
                offsetY += btn.getHeight();
            }
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        dragging = false;

        if (open) {
            buttons.forEach(btn -> btn.mouseReleased(mouseX, mouseY, button));
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        for (ModuleButton btn : buttons) {
            if (btn.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!open) return false;
        for (ModuleButton btn : buttons) {
            if (btn.charTyped(chr, modifiers)) return true;
        }
        return false;
    }
}

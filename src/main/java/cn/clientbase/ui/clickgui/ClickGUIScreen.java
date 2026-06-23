package cn.clientbase.ui.clickgui;

import cn.clientbase.Client;
import cn.clientbase.module.Category;
import cn.clientbase.module.impl.visual.ClickGUI;
import cn.clientbase.module.impl.visual.HUD;
import cn.clientbase.util.IMinecraft;
import cn.clientbase.util.render.FontUtil;
import cn.clientbase.util.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * ClickGUI Screen
 * @author DSJ
 */
public class ClickGUIScreen extends Screen implements IMinecraft {
    private final List<Panel> panels = new ArrayList<>();
    private float exX = 80;
    private float exY = 45;
    private float dragX;
    private float dragY;
    private boolean dragging;
    private Category selectedCategory = Category.Combat;

    public ClickGUIScreen() {
        super(Text.literal("ClickGUI"));
        float startX = 20;
        for (Category category : Category.values()) {
            panels.add(new Panel(category, startX, 20));
            startX += 120;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (isExhibition()) {
            renderExhibition(context, mouseX, mouseY);
            return;
        }

        for (Panel panel : panels) {
            panel.render(context, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isExhibition()) {
            mouseClickedExhibition((int) mouseX, (int) mouseY, button);
            return true;
        }

        for (Panel panel : panels) {
            panel.mouseClicked((int) mouseX, (int) mouseY, button);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isExhibition()) {
            dragging = false;
            getSelectedPanel().getButtons().forEach(btn -> btn.mouseReleased((int) mouseX, (int) mouseY, button));
            return true;
        }

        for (Panel panel : panels) {
            panel.mouseReleased((int) mouseX, (int) mouseY, button);
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isExhibition()) {
            ClickGUI clickGUI = Client.instance.getModuleManager().getModule(ClickGUI.class);
            int boundKey = clickGUI != null ? clickGUI.getKey() : GLFW.GLFW_KEY_RIGHT_SHIFT;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == boundKey) {
                close();
                return true;
            }
            for (ModuleButton button : getSelectedPanel().getButtons()) {
                if (button.keyPressed(keyCode, scanCode, modifiers)) return true;
            }
            return true;
        }

        for (Panel panel : panels) {
            if (panel.keyPressed(keyCode, scanCode, modifiers)) return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (isExhibition()) {
            for (ModuleButton button : getSelectedPanel().getButtons()) {
                if (button.charTyped(chr, modifiers)) return true;
            }
            return true;
        }

        for (Panel panel : panels) {
            if (panel.charTyped(chr, modifiers)) return true;
        }

        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private boolean isExhibition() {
        HUD hud = Client.instance.getModuleManager().getModule(HUD.class);
        return hud != null && hud.isExhibitionStyle();
    }

    private void renderExhibition(DrawContext context, int mouseX, int mouseY) {
        if (dragging) {
            exX = mouseX - dragX;
            exY = mouseY - dragY;
        }

        float width = 340;
        float height = 340;
        drawBordered(context, exX - 0.5f, exY - 0.5f, width + 1, height + 1, 0x00000000, 0xFF0A0A0A);
        drawBordered(context, exX, exY, width, height, 0x00000000, 0xFF3C3C3C);
        drawBordered(context, exX + 2, exY + 2, width - 4, height - 4, 0xFF161616, 0xFF282828);
        RenderUtil.drawRect(context, exX + 3, exY + 4, 37, height - 7, 0xFF0C0C0C);
        RenderUtil.drawRect(context, exX + 40, exY + 4, width - 43, height - 7, 0xFF121212);
        RenderUtil.drawGradientRect(context, exX + 3, exY + 3, 172, 1, 0xFF37B1DA, 0xFFCC4DC6, true);
        RenderUtil.drawGradientRect(context, exX + 175, exY + 3, 162, 1, 0xFFCC4DC6, 0xFFCCE335, true);

        float catY = exY + 15;
        for (Category category : Category.values()) {
            boolean selected = category == selectedCategory;
            boolean hovered = mouseX >= exX + 3 && mouseX <= exX + 40 && mouseY >= catY && mouseY <= catY + 36;
            int text = selected ? 0xFFFFFFFF : hovered ? 0xFFCCCCCC : 0xFF777777;
            if (selected) {
                RenderUtil.drawRect(context, exX + 3, catY, 37, 36, 0xFF1C1C1C);
                RenderUtil.drawRect(context, exX + 3, catY, 1, 36, Client.instance.getModuleManager().getModule(HUD.class).getColor(2));
            }
            String icon = category.getName().substring(0, 1);
            FontUtil.drawStringWithShadow(context, icon, exX + 20 - FontUtil.getStringWidth(icon) / 2f, catY + 14, text);
            catY += 40;
        }

        FontUtil.drawStringWithShadow(context, selectedCategory.getName(), exX + 55, exY + 12, 0xFFE6E6E6);
        RenderUtil.drawRect(context, exX + 55, exY + 25, width - 70, 1, 0xFF242424);

        float y = exY + 34;
        for (ModuleButton button : getSelectedPanel().getButtons()) {
            y += button.renderExhibition(context, mouseX, mouseY, exX + 55, y, width - 72);
            y += 2;
            if (y > exY + height - 14) break;
        }
    }

    private void mouseClickedExhibition(int mouseX, int mouseY, int button) {
        if (button == 0 && mouseX >= exX && mouseX <= exX + 340 && mouseY >= exY && mouseY <= exY + 12) {
            dragging = true;
            dragX = mouseX - exX;
            dragY = mouseY - exY;
            return;
        }

        float catY = exY + 15;
        for (Category category : Category.values()) {
            if (button == 0 && mouseX >= exX + 3 && mouseX <= exX + 40 && mouseY >= catY && mouseY <= catY + 36) {
                selectedCategory = category;
                return;
            }
            catY += 40;
        }

        float y = exY + 34;
        for (ModuleButton moduleButton : getSelectedPanel().getButtons()) {
            moduleButton.mouseClickedExhibition(mouseX, mouseY, button, exX + 55, y, 268);
            y += moduleButton.getHeight() + 2;
        }
    }

    private Panel getSelectedPanel() {
        for (Panel panel : panels) {
            if (panel.getCategory() == selectedCategory) return panel;
        }
        return panels.getFirst();
    }

    private void drawBordered(DrawContext context, float x, float y, float width, float height, int fill, int border) {
        RenderUtil.drawRect(context, x, y, width, height, border);
        RenderUtil.drawRect(context, x + 0.5f, y + 0.5f, width - 1, height - 1, fill);
    }
}

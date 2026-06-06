package cn.clientbase.ui.clickgui.components.impl;

import cn.clientbase.module.value.impl.StringValue;
import cn.clientbase.ui.clickgui.components.ValueComponent;
import cn.clientbase.util.render.FontUtil;
import cn.clientbase.util.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class StringComponent extends ValueComponent {
    private boolean focused;
    private long lastBlink;
    private boolean caretVisible = true;

    public StringComponent(StringValue value) {
        super(value);
    }

    private StringValue string() {
        return (StringValue) getValue();
    }

    @Override
    public float render(DrawContext context, int mouseX, int mouseY, float x, float y, float width) {
        RenderUtil.drawRect(context, x, y, width, 15, new Color(0, 0, 0, focused ? 160 : 120).getRGB());

        String label = getValue().getName() + ": ";
        String text = string().getValue();
        String caret = "";
        if (focused) {
            if (System.currentTimeMillis() - lastBlink > 500) {
                caretVisible = !caretVisible;
                lastBlink = System.currentTimeMillis();
            }
            caret = caretVisible ? "_" : "";
        }

        FontUtil.drawStringWithShadow(context, label + text + caret, x + 6, y + 4, focused ? 0xFFFFFFFF : 0xFFAAAAAA);
        return lastHeight = 15;
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button, float x, float y, float width) {
        if (button == 0) {
            focused = hovered(mouseX, mouseY, x, y, width, 15);
            if (focused) {
                lastBlink = System.currentTimeMillis();
                caretVisible = true;
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                String v = string().getValue();
                if (!v.isEmpty()) string().setValue(v.substring(0, v.length() - 1));
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> {
                focused = false;
                return true;
            }
            default -> {
                return true; // swallow other keys while focused so they don't leak to the game
            }
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!focused) return false;
        if (chr == '§' || chr < 32) return true;
        string().setValue(string().getValue() + chr);
        return true;
    }
}

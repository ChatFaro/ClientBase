package cn.clientbase.module.impl.visual;

import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.ui.clickgui.ClickGUIScreen;
import org.lwjgl.glfw.GLFW;

public final class ClickGUI extends Module {

    public ClickGUI() {
        super("ClickGUI", Category.Visual);
        setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        setDescription("ClickGUI");
    }

    @Override
    public void onEnable() {
        openScreen();
        setEnabled(false);
    }

    public void openScreen() {
        if (mc.player == null || mc.world == null) return;
        mc.setScreen(new ClickGUIScreen());
    }
}

package cn.clientbase.module.impl.visual;

import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import org.lwjgl.glfw.GLFW;

public final class ClickGUI extends Module {

    public ClickGUI() {
        super("ClickGUI", Category.Visual);
        setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        setDescription("ClickGUI");
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) return;

        mc.setScreen(instance.getClickGUIScreen());
        toggle();
    }
}

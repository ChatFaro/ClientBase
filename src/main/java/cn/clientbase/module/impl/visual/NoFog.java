package cn.clientbase.module.impl.visual;

import cn.clientbase.module.Category;
import cn.clientbase.module.Module;

public class NoFog extends Module {
    public NoFog() {
        super("NoFog", Category.Visual);
        setDescription("Remove haze from the edge of the world");
    }
}
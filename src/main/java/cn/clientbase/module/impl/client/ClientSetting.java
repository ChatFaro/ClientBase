package cn.clientbase.module.impl.client;

import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.value.impl.BoolValue;
import cn.clientbase.module.value.impl.MultiBoolValue;
import lombok.Getter;

@Getter
public class ClientSetting extends Module {

    private final MultiBoolValue target = new MultiBoolValue("Target",
            new BoolValue("Player", true),
            new BoolValue("Dead", false),
            new BoolValue("Villager", false),
            new BoolValue("Invisible", false),
            new BoolValue("Mob", false),
            new BoolValue("Animal", false)
    );

    public ClientSetting() {
        super("ClientSetting", Category.Client);
        setEnabled(true);
        setHidden(true);
    }

    @Override
    public void onDisable() {
        setEnabled(true);
    }

}

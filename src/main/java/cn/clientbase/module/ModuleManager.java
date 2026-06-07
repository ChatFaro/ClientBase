package cn.clientbase.module;

import cn.clientbase.Client;
import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.KeyEvent;
import cn.clientbase.module.impl.client.ClientSetting;
import cn.clientbase.module.impl.combat.KillAura;
import cn.clientbase.module.impl.combat.Velocity;
import cn.clientbase.module.impl.misc.Timer;
import cn.clientbase.module.impl.movement.FastWeb;
import cn.clientbase.module.impl.movement.Scaffold;
import cn.clientbase.module.impl.movement.Sprint;
import cn.clientbase.module.impl.movement.Strafe;
import cn.clientbase.module.impl.player.ChestStealer;
import cn.clientbase.module.impl.player.InventoryManager;
import cn.clientbase.module.impl.player.NoJumpDelay;
import cn.clientbase.module.impl.visual.*;
import cn.clientbase.module.value.Value;
import cn.clientbase.util.IMinecraft;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.*;

@Getter
public class ModuleManager implements IMinecraft {
    private final Map<String, Module> moduleMap = new LinkedHashMap<>();

    public ModuleManager() {
        instance.getEventManager().register(this);

        addModules(
                 new Sprint(),
                 new Timer(),
                 new NoJumpDelay(),
                 new ChestStealer(),
                 new InventoryManager(),
                 new Velocity(),
                 new ClickGUI(),                 new HUD(),
                 new NoHurtCam(),
                 new NoFog(),
                 new Brightness(),
                 new ModuleList(),
                 new WaterMark(),
                 new CustomText(),
                 new Strafe(),
                 new WorldTweaks(),
                 new KillAura(),
                 new Animation(),
                 new ClientSetting(),
                 new ESP(),
                 new FastWeb(),
                 new Scaffold()
        );

        sortModules();
    }

    public void addModules(Module... modulesArray) {
        for (Module module : modulesArray) {
            reflectModuleValues(module);
            moduleMap.put(module.getClass().getSimpleName(), module);
        }
    }

    private void reflectModuleValues(Module module) {
        try {
            Class<?> clazz = module.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Value.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object valueObject = field.get(module);
                        if (valueObject != null) module.getValues().add((Value) valueObject);
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
            Client.logger.debug(e.getMessage());
        }
    }

    private void sortModules() {
        List<Module> moduleList = new ArrayList<>(moduleMap.values());
        moduleList.sort(Comparator.comparing(Module::getName));
        moduleMap.clear();
        for (Module module : moduleList) moduleMap.put(module.getClass().getSimpleName(), module);
    }

    public <T extends Module> T getModule(Class<T> clazz) {
        return clazz.cast(moduleMap.get(clazz.getSimpleName()));
    }

    @EventTarget
    private void onKey(KeyEvent event) {
        if (event.getKey() == 0 || mc.currentScreen != null) return;

        for (Module module : moduleMap.values()) {
            if (module.getKey() == event.getKey()) {
                module.toggle();
            }
        }
    }
}
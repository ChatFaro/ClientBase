package cn.clientbase;

import cn.clientbase.command.CommandManager;
import cn.clientbase.config.ConfigManager;
import cn.clientbase.event.base.EventManager;
import cn.clientbase.manager.RotationManager;
import cn.clientbase.manager.TargetManager;
import cn.clientbase.module.ModuleManager;
import cn.clientbase.ui.clickgui.ClickGUIScreen;
import cn.clientbase.ui.font.FontManager;
import cn.clientbase.ui.skia.SkiaManager;
import cn.clientbase.util.IMinecraft;
import lombok.Getter;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Client Base.
 * @author DSJ
 */
@Getter
public class Client implements IMinecraft {
    public static Client instance;
    public static Logger logger;
    public static String name = "Client Base";
    public static String version = "Alpha";

    /**
     * FIFO queue of deferred player actions, drained one-per-tick inside
     * {@code MixinClientWorld.tickEntity} BEFORE the local player ticks
     * (faithful port of OpenZen's {@code ClientBase.delayPackets} +
     * {@code ClientLevelPatch} drain). Used by Scaffold's clutch path to inject
     * an extra rotation packet and re-run its tick logic.
     */

    /*
    Todo List:
    1.Fix Scaffold @Claude :))))))))))))))))))
    2.Fix Velocity
    3.Add KeepSprint   Done.
    4.Add ChestStealer and InventoryManager
     */

    public static final ConcurrentLinkedQueue<Runnable> delayPackets = new ConcurrentLinkedQueue<>();

    private EventManager eventManager;
    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private TargetManager targetManager;
    private RotationManager rotationManager;
    private ClickGUIScreen clickGUIScreen;
    private SkiaManager skiaManager;
    private FontManager fontManager;

    public void init() {
        eventManager =  new EventManager();
        moduleManager  = new ModuleManager();
        commandManager = new CommandManager();
        configManager = new ConfigManager();
        targetManager = new TargetManager();
        rotationManager  = new RotationManager();

        clickGUIScreen  = new ClickGUIScreen();
        skiaManager = new SkiaManager();
        fontManager  = new FontManager();
    }

    public void shutdown() {
        configManager.saveAll();
        skiaManager.destroy();
        fontManager.destroy();
    }
}

package cn.injection;

import cn.clientbase.Client;
import cn.clientbase.event.impl.KeyEvent;
import cn.clientbase.module.impl.visual.ClickGUI;
import cn.clientbase.util.IMinecraft;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard implements IMinecraft {

    @Inject(method = "onKey", at = @At(value="HEAD"), cancellable = true)
    private void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action == 1) {
            if (Client.instance == null || Client.instance.getModuleManager() == null) return;
            ClickGUI clickGUI = Client.instance.getModuleManager() != null
                    ? Client.instance.getModuleManager().getModule(ClickGUI.class)
                    : null;
            if (clickGUI != null && clickGUI.getKey() == key && mc.currentScreen == null) {
                clickGUI.openScreen();
                clickGUI.setEnabled(false);
                ci.cancel();
                return;
            }
            KeyEvent event = new KeyEvent(key);
            Client.instance.getEventManager().call(event);
        }
    }
}

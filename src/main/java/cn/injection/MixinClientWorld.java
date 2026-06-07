package cn.injection;

import cn.clientbase.Client;
import cn.clientbase.util.IMinecraft;
import cn.clientbase.util.Util;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class MixinClientWorld implements IMinecraft {

    /**
     * Drain one deferred runnable from {@link Client#delayPackets} before the local
     * player ticks, cancelling the original tick for that frame. Faithful port of
     * OpenZen's {@code ClientLevelPatch.onTickEntity}.
     */
    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    private void drainDelayPackets(Entity entity, CallbackInfo ci) {
        if (!Client.delayPackets.isEmpty() && entity == IMinecraft.mc.player) {
            Runnable delayed = Client.delayPackets.poll();
            if (delayed != null) {
                delayed.run();
            }
            ci.cancel();
        }
    }

    @Inject(method = "tickEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;resetPosition()V", shift = At.Shift.AFTER), cancellable = true)
    private void tickEntity(Entity entity, CallbackInfo ci) {
        if (Util.skipTicks > 0 && entity == IMinecraft.mc.player) {
            Util.skipTicks--;
            ci.cancel();
        }
    }
}

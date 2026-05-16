package cn.injection;

import cn.clientbase.event.impl.JumpEvent;
import cn.clientbase.event.impl.MoveMathEvent;
import cn.clientbase.event.impl.RotationEvent;
import cn.clientbase.util.IMinecraft;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements IMinecraft {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void travel(Vec3d movementInput, CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;

        if (((Object) this) instanceof ClientPlayerEntity) {
            MoveMathEvent event = new MoveMathEvent();
            instance.getEventManager().call(event);

            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Redirect(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float jump(LivingEntity entity) {
        if (mc.player == null || mc.world == null) return entity.getYaw();

        if (entity == mc.player) {
            JumpEvent event = new JumpEvent(entity.getYaw());
            instance.getEventManager().call(event);
            return event.getYaw();
        }

        return entity.getYaw();
    }

    @Redirect(method = "turnHead", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float turnHead(LivingEntity entity) {
        if (entity == mc.player) {
            RotationEvent event = new RotationEvent(new float[]{entity.getYaw(), 0}, new float[]{0, 0});
            instance.getEventManager().call(event);
            return event.getRotation()[0];
        }

        return entity.getYaw();
    }
}

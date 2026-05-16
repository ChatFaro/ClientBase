package cn.clientbase.util.player;

import cn.clientbase.module.impl.client.ClientSetting;
import cn.clientbase.util.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.PlayerEntity;

@UtilityClass
public class EntityUtil implements IMinecraft {

    public boolean isSelected(Entity entity) {
        return isSelected(entity, true);
    }

    public boolean isSelected(Entity entity,  boolean checkSelf) {
        if (!(entity instanceof LivingEntity livingEntity)) return false;

        ClientSetting clientSetting = instance.getModuleManager().getModule(ClientSetting.class);

        if (!clientSetting.getTarget().isEnabled("Invisible") && livingEntity.isInvisible()) {
            return false;
        }

        if (!livingEntity.isAlive() || livingEntity.isSpectator()) {
            return false;
        }

        if (livingEntity instanceof PlayerEntity player) {
            if (checkSelf && player.equals(mc.player)) {
                return false;
            }

            return clientSetting.getTarget().isEnabled("Player");
        }

        boolean isMob = clientSetting.getTarget().isEnabled("Mob") && isMob(livingEntity);
        boolean isAnimal = clientSetting.getTarget().isEnabled("Animal") && isAnimal(livingEntity);
        boolean isVillager = clientSetting.getTarget().isEnabled("Villager") && livingEntity instanceof VillagerEntity;

        return isMob || isAnimal || isVillager;
    }

    public boolean isAnimal(final Entity entity) {
        return entity instanceof AnimalEntity
                || entity instanceof SquidEntity
                || entity instanceof IronGolemEntity
                || entity instanceof BatEntity;
    }

    public boolean isMob(final Entity entity) {
        return entity instanceof Monster
                || entity instanceof SlimeEntity
                || entity instanceof GhastEntity
                || entity instanceof ShulkerEntity
                || entity instanceof EnderDragonEntity;
    }
}
package cn.clientbase.module.impl.combat;

import cn.clientbase.module.Module;
import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.MoveInputEvent;
import cn.clientbase.event.impl.PacketEvent;
import cn.clientbase.event.impl.UpdateEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.value.impl.ModeValue;
import lombok.Getter;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;

@Getter
public class Velocity extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "Legit");
    private boolean jump = false;

    public Velocity() {
        super("Velocity", Category.Combat);
        setDescription("Modify the velocity of the player.");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.getPacket();
        if (event.getType() == PacketEvent.Type.Received) {
            switch (mode.getValue()) {
                case "Vanilla" -> {
                    if (packet instanceof EntityVelocityUpdateS2CPacket velocity) {
                        if (velocity.getEntityId() == mc.player.getId()) {
                            event.setCancelled(true);
                        }
                    }
                }

                case "Legit" -> {
                    if (packet instanceof EntityVelocityUpdateS2CPacket velocity) {
                        if (velocity.getEntityId() == mc.player.getId()) {
                            jump = true;
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null) return;

        if (jump) {
            event.setJumping(true);
            jump = false;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(mode.getValue());
    }
}
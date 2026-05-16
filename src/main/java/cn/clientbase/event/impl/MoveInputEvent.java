package cn.clientbase.event.impl;

import cn.clientbase.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MoveInputEvent extends Event {
    private float forward;
    private float strafe;
    private boolean jumping;
    private boolean sneaking;
}
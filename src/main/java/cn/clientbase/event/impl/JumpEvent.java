package cn.clientbase.event.impl;

import cn.clientbase.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class JumpEvent extends Event {
    private float yaw;
}

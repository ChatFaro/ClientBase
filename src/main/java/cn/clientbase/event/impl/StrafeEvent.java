package cn.clientbase.event.impl;

import cn.clientbase.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class StrafeEvent extends Event {
    private float yaw;
}
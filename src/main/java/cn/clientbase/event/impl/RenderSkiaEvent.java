package cn.clientbase.event.impl;

import cn.clientbase.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.skija.Canvas;

@Setter
@Getter
@AllArgsConstructor
public class RenderSkiaEvent extends Event {
    private final Canvas canvas;
}

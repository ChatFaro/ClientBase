package cn.injection;

import cn.clientbase.event.impl.Render2DEvent;
import cn.clientbase.util.IMinecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class MixinInGameHud implements IMinecraft {

    @Inject(method = "render", at = @At(value = "HEAD"))
    private void render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;

        Render2DEvent event = new Render2DEvent(context, tickCounter.getTickDelta(true));
        instance.getEventManager().call(event);

        // Flush any buffered 2D draws (notably text from DrawContext#drawText, which is
        // batched into the immediate buffer rather than drawn instantly) BEFORE Skia runs.
        // SkiaManager#render dirties GL state (glClear, resetGLAll, texture/blend binds), so
        // text flushed afterwards loses its glyph-atlas texture and renders as black boxes.
        context.draw();

        instance.getSkiaManager().render();
    }
}

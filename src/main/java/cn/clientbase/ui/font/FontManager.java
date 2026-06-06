package cn.clientbase.ui.font;

import cn.clientbase.Client;
import cn.clientbase.ui.font.base.SkiaFont;
import org.jetbrains.skija.Data;
import org.jetbrains.skija.Typeface;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FontManager {
    private final Map<String, SkiaFont> cache = new ConcurrentHashMap<>();

    public SkiaFont getFont(int size) {
        return getFont("MiSans-Normal.ttf", size);
    }

    public SkiaFont getMediumFont(int size) {
        return getFont("MiSans-Medium.ttf", size);
    }

    public SkiaFont getSemiboldFont(int size) {
        return getFont("MiSans-Semibold.ttf", size);
    }

    public SkiaFont getBoldFont(int size) {
        return getFont("MiSans-Bold.ttf", size);
    }

    private SkiaFont getFont(String name, int size) {
        return cache.computeIfAbsent(name + ":" + size, k -> create(name, size));
    }

    public SkiaFont create(String name, int size) {
        String path = "/assets/clientbase/fonts/" + name;
        try (InputStream is = SkiaFont.class.getResourceAsStream(path)) {
            if (is == null) {
                Client.logger.error("Font resource not found on classpath: {} (is the ttf packaged under resources?)", path);
                return new SkiaFont(Typeface.makeDefault(), size);
            }

            byte[] bytes = is.readAllBytes();
            // NOTE: Typeface.makeFromData references the underlying SkData; if the Data is
            // closed (e.g. via try-with-resources) before the typeface is used, the native
            // buffer is freed and every glyph renders as tofu. Keep the Data alive for the
            // lifetime of the typeface instead of closing it here.
            Data fontData = Data.makeFromBytes(bytes);
            Typeface typeface = Typeface.makeFromData(fontData);
            if (typeface == null) {
                Client.logger.error("Skija failed to parse font (returned null typeface): {}", path);
                fontData.close();
                return new SkiaFont(Typeface.makeDefault(), size);
            }
            return new SkiaFont(typeface, size);
        } catch (Throwable t) {
            Client.logger.error("Failed to load font {}: {}", path, t.toString(), t);
            return new SkiaFont(Typeface.makeDefault(), size);
        }
    }

    public void destroy() {
        cache.values().forEach(SkiaFont::close);
        cache.clear();
    }
}
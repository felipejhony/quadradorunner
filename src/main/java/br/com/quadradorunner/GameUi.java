package br.com.quadradorunner;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared local resources for Java2D and Swing; no installed fonts or global UI defaults. */
final class GameUi {
    static final Color SURFACE = new Color(15, 29, 46);
    static final Color EDGE = new Color(51, 76, 96);
    static final Color TEXT = new Color(225, 237, 247);
    static final Color MUTED = new Color(157, 179, 196);
    static final Color MINT = new Color(83, 227, 192);
    static final Color DANGER = new Color(255, 148, 142);
    private static final Font REGULAR = load("/fonts/Oxanium-Regular.ttf", Font.PLAIN);
    private static final Font BOLD = load("/fonts/Oxanium-Bold.ttf", Font.BOLD);

    private GameUi() { }

    static Font regular(float size) { return REGULAR.deriveFont(size); }
    static Font bold(float size) { return BOLD.deriveFont(size); }

    private static Font load(String resource, int fallbackStyle) {
        try (InputStream input = GameUi.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing font: " + resource);
            return Font.createFont(Font.TRUETYPE_FONT, input);
        } catch (IOException | FontFormatException exception) {
            Logger.getLogger(GameUi.class.getName()).log(Level.WARNING,
                "Could not load bundled font; using SansSerif", exception);
            return new Font(Font.SANS_SERIF, fallbackStyle, 14);
        }
    }
}

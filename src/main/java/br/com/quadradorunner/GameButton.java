package br.com.quadradorunner;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.BorderFactory;

/** Keeps JButton actions/accessibility while drawing the game's rounded menu controls. */
final class GameButton extends JButton {
    private final boolean destructive;

    GameButton(String text, boolean destructive) {
        super(text);
        this.destructive = destructive;
        setFont(GameUi.bold(14));
        setForeground(destructive ? GameUi.DANGER : GameUi.TEXT);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
    }

    void setKeyboardSelected(boolean selected) {
        getModel().setSelected(selected);
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean highlighted = isEnabled() && (getModel().isSelected() || getModel().isRollover() || hasFocus());
            Color fill = destructive ? new Color(45, 28, 40) : GameUi.SURFACE;
            if (highlighted) fill = destructive ? new Color(73, 37, 48) : new Color(27, 58, 70);
            if (getModel().isPressed() && isEnabled()) fill = fill.brighter();
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.setColor(!isEnabled() ? GameUi.EDGE
                : destructive ? GameUi.DANGER : highlighted ? GameUi.MINT : GameUi.EDGE);
            if (getModel().isSelected()) g.setStroke(new java.awt.BasicStroke(3));
            g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 12, 12);
            g.setFont(getFont());
            g.setColor(isEnabled() ? getForeground() : GameUi.MUTED);
            var metrics = g.getFontMetrics();
            g.drawString(getText(), (getWidth() - metrics.stringWidth(getText())) / 2,
                (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally { g.dispose(); }
    }
}

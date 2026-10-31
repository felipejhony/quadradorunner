package br.com.quadradorunner;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("Quadrado Runner • Java2D");
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            GamePanel panel = new GamePanel();
            window.setContentPane(panel);
            window.addWindowFocusListener(new WindowAdapter() {
                @Override public void windowLostFocus(WindowEvent event) {
                    panel.pauseForFocusLoss();
                }
                @Override public void windowGainedFocus(WindowEvent event) {
                    panel.audioFocusGained();
                }
            });
            window.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent event) { panel.closeAudio(); }
            });
            window.pack();
            window.setResizable(false);
            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }
}

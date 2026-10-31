package br.com.quadradorunner;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import java.io.IOException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        GameProgressStore store = new GameProgressStore();
        GameProgress progress;
        try { progress = store.load(); }
        catch (IOException exception) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                "Não foi possível carregar o progresso. O save foi preservado.\n" + exception.getMessage(),
                "Erro ao carregar", JOptionPane.ERROR_MESSAGE));
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("Quadrado Runner • Java2D");
            window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            GameSession session = new GameSession(List.of(Level.standard()), progress);
            ProgressAutosave autosave = new ProgressAutosave(store);
            GamePanel panel = new GamePanel(session, GameAudio::new,
                snapshot -> autosave.save(snapshot, failure -> {
                    if (failure != null) SwingUtilities.invokeLater(() -> showSaveFailure(window, failure));
                }));
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
                @Override public void windowClosing(WindowEvent event) {
                    if (!window.isEnabled()) return;
                    window.setEnabled(false);
                    panel.pauseForFocusLoss();
                    autosave.save(session.current().progress(), failure -> SwingUtilities.invokeLater(() -> {
                        if (failure != null) {
                            showSaveFailure(window, failure);
                        } else {
                            autosave.close();
                            window.dispose();
                        }
                    }));
                }
            });
            window.pack();
            window.setResizable(false);
            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }

    private static void showSaveFailure(JFrame window, IOException failure) {
        window.setEnabled(true);
        JOptionPane.showMessageDialog(window,
            "Não foi possível salvar o progresso. Ele continua em memória.\n"
                + "Tente fechar novamente para repetir a gravação.\n" + failure.getMessage(),
            "Erro ao salvar", JOptionPane.ERROR_MESSAGE);
    }
}

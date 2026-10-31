package br.com.quadradorunner;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import java.io.IOException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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
            GameSession session = new GameSession(Level.campaign(), progress);
            ProgressAutosave autosave = new ProgressAutosave(store);
            GamePanel panel = new GamePanel(session, GameAudio::new,
                snapshot -> autosave.save(snapshot, failure -> {
                    if (failure != null) SwingUtilities.invokeLater(() -> showSaveFailure(window, failure));
                }), requestedPanel -> resetGame(window, requestedPanel, autosave));
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
                    if (!window.isEnabled() || panel.isResetting()) return;
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

    private static void resetGame(JFrame window, GamePanel panel, ProgressAutosave autosave) {
        if (panel.isResetting() || !panel.confirmReset()) return;
        panel.beginReset();
        autosave.reset(failure -> SwingUtilities.invokeLater(() -> {
            panel.finishReset(failure == null);
            if (failure != null) JOptionPane.showMessageDialog(window,
                "Não foi possível resetar o save. O progresso anterior foi mantido.\n" + failure.getMessage(),
                "Erro ao resetar", JOptionPane.ERROR_MESSAGE);
        }));
    }
}

package br.com.quadradorunner;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Explicit destructive action, with cancel as the default and Escape/close as cancellation. */
final class ResetConfirmation {
    private ResetConfirmation() { }

    static boolean show(Component owner) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner),
            "Recomeçar do zero?", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel content = new JPanel(new BorderLayout(0, 22));
        content.setBackground(GameUi.SURFACE);
        content.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GameUi.EDGE), BorderFactory.createEmptyBorder(28, 28, 24, 28)));
        JLabel title = new JLabel("RECOMEÇAR DO ZERO?");
        title.setFont(GameUi.bold(24));
        title.setForeground(GameUi.DANGER);
        content.add(title, BorderLayout.NORTH);
        JLabel description = new JLabel("<html>Saldo, argolas, habilidades e conclusões serão apagados.<br>"
            + "Os cristais voltam e somente a fase 1 fica liberada.<br><br>"
            + "Esta ação não pode ser desfeita. Para repetir só a tentativa, use R.</html>");
        description.setFont(GameUi.regular(14));
        description.setForeground(GameUi.TEXT);
        content.add(description, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        actions.setOpaque(false);
        GameButton cancel = new GameButton("Manter progresso", false);
        GameButton reset = new GameButton("Sim, zerar tudo", true);
        boolean[] confirmed = {false};
        cancel.addActionListener(event -> dialog.dispose());
        reset.addActionListener(event -> { confirmed[0] = true; dialog.dispose(); });
        actions.add(cancel);
        actions.add(reset);
        content.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(cancel);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
            KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return confirmed[0];
    }
}

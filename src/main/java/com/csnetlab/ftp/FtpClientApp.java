package com.csnetlab.ftp;

import com.csnetlab.ftp.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class FtpClientApp {
    private FtpClientApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fall back to Swing's default look and feel.
            }

            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}


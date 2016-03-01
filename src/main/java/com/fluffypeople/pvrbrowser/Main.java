package com.fluffypeople.pvrbrowser;

import java.util.prefs.Preferences;
import javax.swing.UIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String args[]) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            log.error("Can't change look and feel", ex);
        }

        final Preferences prefs = Preferences.userNodeForPackage(Main.class);

        log.debug("Download directory {}", prefs.get(MediaBrowser.DOWNLOAD_DIRECTORY_KEY, "Uknown"));

        java.awt.EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                new MediaBrowser(prefs).setVisible(true);
            }
        });
    }

}

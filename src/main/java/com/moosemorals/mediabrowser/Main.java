/*
 * The MIT License
 *
 * Copyright 2016 Osric Wilkinson <osric@fluffypeople.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.moosemorals.mediabrowser;

import com.moosemorals.mediabrowser.ui.About;
import com.moosemorals.mediabrowser.ui.UI;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Entry point for the application.
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
public class Main implements Runnable, ActionListener {

    public static final String KEY_FRAME_KNOWN = "frame_bounds";
    public static final String KEY_FRAME_HEIGHT = "frame_height";
    public static final String KEY_MESSAGE_ON_COMPLETE = "message_on_complete";
    public static final String KEY_AUTO_DOWNLOAD = "auto_download";
    public static final String KEY_DOWNLOAD_DIRECTORY = "download_directory";
    public static final String KEY_MINIMISE_TO_TRAY = "minimise_to_tray";
    public static final String KEY_HORIZONTAL_DIVIDER_LOCATION = "horizontal_divider_location";
    public static final String KEY_VERTICAL_DIVIDER_LOCATION = "vertical_divider_location";
    public static final String KEY_FRAME_WIDTH = "frame_width";
    public static final String KEY_FRAME_LEFT = "frame_left";
    public static final String KEY_FRAME_TOP = "frame_top";
    public static final String KEY_SAVE_DOWNLOAD_LIST = "save_download_list";
    public static final String KEY_SAVE_DOWNLOAD_COUNT = "save_download_count";
    public static final String KEY_SAVE_DOWNLOAD_REMOTE = "save_download_remote";
    public static final String KEY_SAVE_DOWNLOAD_LOCAL = "save_download_local";

    public static void main(String args[]) {
        log.info("***** STARTUP *****");

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                log.info("***** SHUTDOWN *****");
            }
        });

        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            log.error("Can't change look and feel", ex);
        }

        Preferences prefs = Preferences.userNodeForPackage(Main.class);

        // Set some defaults
        if (prefs.get(KEY_AUTO_DOWNLOAD, null) == null) {
            prefs.putBoolean(KEY_AUTO_DOWNLOAD, true);
        }
        if (prefs.get(KEY_MINIMISE_TO_TRAY, null) == null) {
            prefs.putBoolean(KEY_MINIMISE_TO_TRAY, false);
        }
        if (prefs.get(KEY_FRAME_KNOWN, null) == null) {
            prefs.putBoolean(KEY_FRAME_KNOWN, false);
        }
        if (prefs.get(KEY_SAVE_DOWNLOAD_LIST, null) == null) {
            prefs.putBoolean(KEY_SAVE_DOWNLOAD_LIST, true);
        }

        new Main(prefs).start();
    }

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private final Preferences preferences;
    private DownloadManager downloader;
    private final PVR pvr;

    private UI ui = null;

    private Main(Preferences prefs) {

        this.preferences = prefs;

        pvr = new PVR(prefs);

    }

    public void start() {
        downloader = DownloadManager.createInstance(this);
        pvr.start();
        SwingUtilities.invokeLater(this);
    }

    @Override
    public void run() {
        // Running in the AWT thread
        ui = new UI(this);
        pvr.addDeviceListener(ui);
        downloader.addDownloadStatusListener(ui);
        ui.setStartActionStatus(downloader.areDownloadsAvailible(), downloader.isDownloading());
        ui.showWindow();
    }

    public PVR getPVR() {
        return pvr;
    }

    public void stop() {
        pvr.stop();
        downloader.stop();

        if (ui != null) {
            ui.stop();
        }
        System.exit(0);
    }

    public DownloadManager getDownloadManager() {
        return downloader;
    }

    public Preferences getPreferences() {
        return preferences;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd == null) {
            cmd = "UKNOWN";
        }

        List<DownloadManager.QueueItem> listSelected;
        List<PVRFile> treeSelected;

        switch (cmd) {
            case UI.ACTION_START_STOP:
                if (downloader.isDownloading()) {
                    downloader.stop();
                } else {
                    downloader.start();
                }
                break;
            case UI.ACTION_QUEUE:
                while (!downloader.isDownloadPathSet()) {
                    downloader.setDownloadPath(ui.showDirectoryChooser(downloader.getDownloadPath()));
                }

                for (PVRFile file : ui.getTreeSelected()) {
                    if (!file.isLocked()) {
                        if (downloader.add(file)) {
                            ui.setStartActionStatus(downloader.areDownloadsAvailible(), downloader.isDownloading());
                        }
                    }
                }
                break;
            case UI.ACTION_UNLOCK:

                try {
                    pvr.unlockFile(ui.getTreeSelected());
                } catch (IOException ex) {
                    log.error("Problem unlocking: {}", ex.getMessage(), ex);
                    // TODO: Show error?
                }

                break;
            case UI.ACTION_CHOOSE_DEFAULT:
                downloader.setDownloadPath(ui.showDirectoryChooser(downloader.getDownloadPath()));
                break;
            case UI.ACTION_CHOOSE:

                listSelected = ui.getListSelected();
                if (!listSelected.isEmpty()) {
                    File downloadPath = ui.showDirectoryChooser(listSelected.get(0).getLocalPath());
                    if (downloadPath != null) {
                        downloader.changeDownloadPath(listSelected, downloadPath);
                    }
                }
                break;
            case UI.ACTION_RESTORE:
                if (ui != null) {
                    ui.showWindow();
                }
                break;
            case UI.ACTION_REMOVE:
                downloader.remove(ui.getListSelected());
                break;
            case UI.ACTION_QUIT:
            case "Exit": // Stupid TrayIcon popup doesn't support Actions!
                stop();
                break;
            case UI.ACTION_ABOUT:
                new About(ui.getImagesForColor(UI.ICON_CONNECTED)).start();
                break;
            case UI.ACTION_TRAY:
                if (!ui.isVisible()) {
                    ui.showWindow();
                }
                break;
            default:
                log.warn("Unknown action {}, ignoring", cmd);
                break;
        }
    }

    public boolean askYesNoQuestion(String question) {
        if (ui != null) {
            return ui.askYesNoQuestion(question);
        } else {
            log.warn("Asking question [{}] but no UI.", question);
            return false;
        }
    }

    public static class ExceptionHandler implements Thread.UncaughtExceptionHandler {

        @Override
        public void uncaughtException(Thread thread, Throwable thrown) {
            log.error("Uncaught exception in thread {}: {}", thread.getName(), thrown.getMessage(), thrown);
            System.exit(1);
        }
    }

}

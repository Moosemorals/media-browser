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

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.UIManager;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Entry point for the application.
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
public class Main implements Runnable, ActionListener {

    static final String KEY_FRAME_KNOWN = "frame_bounds";
    static final String KEY_FRAME_HEIGHT = "frame_height";
    static final String KEY_MESSAGE_ON_COMPLETE = "message_on_complete";
    static final String KEY_AUTO_DOWNLOAD = "auto_download";
    static final String KEY_DOWNLOAD_DIRECTORY = "download_directory";
    static final String KEY_MINIMISE_TO_TRAY = "minimise_to_tray";
    static final String KEY_DIVIDER_LOCATION = "divider_location";
    static final String KEY_FRAME_WIDTH = "frame_width";
    static final String KEY_FRAME_LEFT = "frame_left";
    static final String KEY_FRAME_TOP = "frame_top";
    static final String KEY_SAVE_DOWNLOAD_LIST = "save_download_list";

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

        Main main = new Main(Preferences.userNodeForPackage(Main.class));
        EventQueue.invokeLater(main);
    }

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private final Preferences preferences;
    private final DownloadManager downloader;
    private final PVR pvr;
    private UI ui = null;

    private Main(Preferences prefs) {
        this.preferences = prefs;
        pvr = new PVR();

        pvr.start();
        downloader = new DownloadManager(preferences);
        pvr.addConnectionListener(downloader);

    }

    @Override
    public void run() {
        // Running in the AWT thread
        Thread.currentThread().setUncaughtExceptionHandler(new ExceptionHandler());
        ui = new UI(this);
        pvr.addConnectionListener(ui);
        log.debug("UI ready");
        ui.showWindow();
    }

    PVR getPVR() {
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

    DownloadManager getDownloadManager() {
        return downloader;
    }

    Preferences getPreferences() {
        return preferences;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd == null) {
            cmd = "UKNOWN";
        }

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
                    downloader.setDownloadPath(ui.showFileChooser(downloader.getDownloadPath()));
                }

                for (TreePath p : ui.getTreeSelected()) {
                    PVR.PVRItem item = (PVR.PVRItem) p.getLastPathComponent();
                    if (item.isFile() && !((PVR.PVRFile) item).isHighDef()) {

                        if (downloader.add((PVR.PVRFile) item)) {
                            ui.setStartButtonStatus(downloader.downloadsAvailible(), downloader.isDownloading());
                        }
                    }
                }
                break;
            case UI.ACTION_LOCK:
                for (TreePath p : ui.getTreeSelected()) {
                    PVR.PVRItem item = (PVR.PVRItem) p.getLastPathComponent();
                    if (item.isFile() && ((PVR.PVRFile) item).isLocked()) {
                        PVR.PVRFile file = (PVR.PVRFile) item;
                        try {
                            pvr.unlockFile(file);
                        } catch (IOException ex) {
                            log.error("Problem unlocking " + file.path + "/" + file.filename + ": " + ex.getMessage(), ex);
                            file.setState(PVR.PVRFile.State.Error);
                        }
                    }
                }
                break;
            case UI.ACTION_CHOOSE_DEFAULT:
                downloader.setDownloadPath(ui.showFileChooser(downloader.getDownloadPath()));
                break;
            case UI.ACTION_CHOOSE:
                List<PVR.PVRFile> selected = ui.getListSelected();
                if (!selected.isEmpty()) {
                    File downloadPath = ui.showFileChooser(selected.get(0).getDownloadPath());
                    downloader.changeDownloadPath(selected, downloadPath);
                }
                break;
            case UI.ACTION_REMOVE:
                downloader.remove(ui.getListSelected());
                break;
            case UI.ACTION_QUIT:
                stop();
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

    boolean isDownloading() {
        return downloader.isDownloading();
    }

    static class ExceptionHandler implements Thread.UncaughtExceptionHandler {

        @Override
        public void uncaughtException(Thread thread, Throwable thrown) {
            log.error("Uncaught exception in thread {}: {}", thread.getName(), thrown.getMessage(), thrown);
            System.exit(1);
        }
    }

}

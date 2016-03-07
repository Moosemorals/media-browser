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
import java.util.prefs.Preferences;
import javax.swing.UIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Entry point for the application.
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
public class Main implements Runnable, PVR.ConnectionListener {

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
        pvr = new PVR(this);
        pvr.start();
        downloader = new DownloadManager(preferences);
    }

    @Override
    public void run() {
        // Running in the AWT thread
        Thread.currentThread().setUncaughtExceptionHandler(new ExceptionHandler());
        ui = new UI(this);
        log.debug("UI ready");
        ui.start();
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
    public void onConnect() {
        // do nothing
    }

    @Override
    public void onDisconnect() {
        downloader.stop();
    }

    static class ExceptionHandler implements Thread.UncaughtExceptionHandler {

        @Override
        public void uncaughtException(Thread thread, Throwable thrown) {
            log.error("Uncaught exception in thread {}: {}", thread.getName(), thrown.getMessage(), thrown);
            System.exit(1);
        }
    }

}

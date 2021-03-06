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

import static com.moosemorals.mediabrowser.Main.KEY_SAVE_DOWNLOAD_COUNT;
import static com.moosemorals.mediabrowser.Main.KEY_SAVE_DOWNLOAD_LIST;
import static com.moosemorals.mediabrowser.Main.KEY_SAVE_DOWNLOAD_LOCAL;
import static com.moosemorals.mediabrowser.Main.KEY_SAVE_DOWNLOAD_REMOTE;
import java.awt.EventQueue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Models a remote PVR.
 *
 * <p>
 * PVRs have folders that contain files. Files have a bunch of attributes.
 * (Folders also have attributes).</p>
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
public class PVR implements TreeModel, DeviceListener {

    public static final DateTimeZone DEFAULT_TIMEZONE = DateTimeZone.forID("Europe/London");
    public static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormat.forPattern("YYYY-MM-dd HH-mm").withZone(DEFAULT_TIMEZONE);
    public static final DateTimeFormatter DISPLAY_DATE_AND_TIME = DateTimeFormat.forStyle("MS");
    public static final DateTimeFormatter DISPLAY_TIME = DateTimeFormat.forStyle("-S");

    public static final PeriodFormatter PERIOD_FORMAT = new PeriodFormatterBuilder()
            .appendHours()
            .appendSeparatorIfFieldsBefore("h")
            .appendMinutes()
            .appendSeparatorIfFieldsBefore("m")
            .toFormatter();

    public static final double KILO = 1024;
    public static final double MEGA = KILO * 1024;
    public static final double GIGA = MEGA * 1024;
    public static final double TERA = GIGA * 1024;

    private static final String SIZE_FORMAT = "%.1f %sb";

    /**
     * Convert a number of bytes into something more readable.
     *
     * <p>
     * Note that I've got no time for that "SI bytes" rubbish, 1kb is 1024
     * bytes.</p>
     *
     * @param size long number of bytes to display
     * @return String human readable String showing roughly the same number of
     * bytes.
     */
    public static String humanReadableSize(long size) {
        if (size > TERA) {
            return String.format(SIZE_FORMAT, size / TERA, "T");
        } else if (size > GIGA) {
            return String.format(SIZE_FORMAT, size / GIGA, "G");
        } else if (size > MEGA) {
            return String.format(SIZE_FORMAT, size / MEGA, "M");
        } else if (size > KILO) {
            return String.format(SIZE_FORMAT, size / MEGA, "k");
        } else if (size >= 0) {
            return String.format("%db", size);
        } else {
            return "-b";
        }
    }

    private final Logger log = LoggerFactory.getLogger(PVR.class);
    private final Set<TreeModelListener> treeModelListeners = new HashSet<>();
    private final PVRFolder rootFolder;
    private final AtomicBoolean running;
    private final DlnaScanner dlnaClient;
    private final Preferences prefs;
    private final ScheduledThreadPoolExecutor scheduler;
    private ScheduledFuture<?> scanTask;
    private FtpScanner ftpClient;
    

    PVR(Preferences prefs) {
        rootFolder = new PVRFolder(null, "/", "Humax HDR FOX-T2");

        // OK, this isn't strictly true, but we'll just have to cope.
        rootFolder.setFtpScanned(true);
        rootFolder.setDlnaScanned(true);

        scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setRemoveOnCancelPolicy(true);

        this.prefs = prefs;
        dlnaClient = new DlnaScanner(this);
        running = new AtomicBoolean(false);

        
    }

    @Override
    public Object getRoot() {
        return rootFolder;
    }

    @Override
    public Object getChild(Object parent, int index) {
        return ((PVRFolder) parent).getChild(index);
    }

    @Override
    public int getChildCount(Object parent) {
        return ((PVRFolder) parent).getChildCount();
    }

    @Override
    public boolean isLeaf(Object node) {
        return ((PVRItem) node).isFile();
    }

    @Override
    public void valueForPathChanged(TreePath arg0, Object arg1) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getIndexOfChild(Object p, Object c) {
        if (p != null && p instanceof PVRFolder && c != null && c instanceof PVRItem) {
            return ((PVRFolder) p).getChildIndex((PVRItem) c);
        }
        return -1;
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        synchronized (treeModelListeners) {
            treeModelListeners.add(l);
        }
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        synchronized (treeModelListeners) {
            treeModelListeners.remove(l);
        }
    }

    /**
     * Starts looking for devices to connect to. Starts a new thread and then
     * returns. New devices will be reported asynchronously..
     */
    public void start() {
        if (running.compareAndSet(false, true)) {

            dlnaClient.addDeviceListener(this);
            dlnaClient.startSearch();
        }
    }

    /**
     * Stop any running threads and tidy up.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.debug("Stopping");
            scheduler.shutdown();
            dlnaClient.stop();
            if (ftpClient != null) {
                ftpClient.stop();
            }

            rootFolder.clearChildren();
            notifyTreeStructureUpdate(new TreeModelEvent(this, rootFolder.getTreePath()));
        }
    }

    public void treeWalk(TreeWalker walker, boolean update) {
        rootFolder.treeWalk(this, walker, update);
    }

    public void unlockFile(List<PVRFile> files) throws IOException {
        if (ftpClient != null) {
            ftpClient.unlockFile(files);
        }
    }

    public void moveToFolder(List<PVRFile> files, PVRFolder destination) throws IOException {
        if (ftpClient != null) {
            //    ftpClient.moveToFolder(files, destination);

            for (PVRFile file : files) {
                PVRFolder parent = file.getParent();
                int index = parent.getChildIndex(file);
                notifyTreeNodeRemoved(new TreeModelEvent(this, parent.getTreePath(), new int[]{index}, new Object[]{file}));
                destination.addChild(file);
            }

            notifyTreeStructureUpdate(new TreeModelEvent(this, destination.getTreePath()));

        }
    }

    /**
     * After a scan is completed, remove any items that no longer exist.
     *
     * @param age
     */
    private void removeStaleItems(final long age) {
        final long now = System.currentTimeMillis();
        treeWalk(new TreeWalker() {
            @Override
            public void action(PVRItem item, Iterator it) {
                if (now - item.getLastScanned() > age) {
                    PVRFolder parent = item.getParent();
                    int index = parent.getChildIndex(item);

                    it.remove();

                    notifyTreeNodeRemoved(new TreeModelEvent(this, parent.getTreePath(), new int[]{index}, new Object[]{item}));
                }
            }
        }, true);
    }

    /**
     * Adds a Folder to the tree. Will return an existing folder if it can.
     *
     * @param parent {@link PVRFolder} parent, must not be null.
     * @param folderName String PVRs name of the folder.
     * @return {@link PVRFolder} either an existing Folder, or a new one.
     */
    PVRFolder addFolder(PVRFolder parent, String folderName) {
        PVRItem child = parent.getChild(folderName);

        if (child != null) {
            if (child instanceof PVRFolder) {
                return (PVRFolder) child;
            } else {
                throw new RuntimeException("Can't add folder [" + folderName + "] to " + parent.getRemotePath() + ": Already exists as file");
            }
        }

        PVRFolder folder = new PVRFolder(parent, parent.getRemotePath() + folderName + "/", folderName);
        parent.addChild(folder);

        notifyTreeNodeInserted(new TreeModelEvent(this, parent.getTreePath(), new int[]{parent.getChildIndex(folder)}, new Object[]{folder}));
        notifyTreeStructureUpdate(new TreeModelEvent(this, parent.getTreePath()));
        return folder;
    }

    PVRFile addFile(PVRFolder parent, String fileName) {
        PVRItem child = parent.getChild(fileName);

        if (child != null) {
            if (child instanceof PVRFile) {
                return (PVRFile) child;
            } else {
                throw new RuntimeException("Can't add file [" + fileName + "] to " + parent.getRemotePath() + ": Already exists as folder");
            }
        }

        PVRFile file = new PVRFile(parent, parent.getRemotePath() + fileName, fileName);
        parent.addChild(file);

        notifyTreeNodeInserted(new TreeModelEvent(this, parent.getTreePath(), new int[]{parent.getChildIndex(file)}, new Object[]{file}));
        notifyTreeStructureUpdate(new TreeModelEvent(this, parent.getTreePath()));
        return file;

    }

    void notifyTreeNodeRemoved(final TreeModelEvent e) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                synchronized (treeModelListeners) {
                    for (final TreeModelListener l : treeModelListeners) {
                        l.treeNodesRemoved(e);
                    }
                }
            }
        });
    }

    void notifyTreeNodeInserted(final TreeModelEvent e) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                synchronized (treeModelListeners) {
                    for (final TreeModelListener l : treeModelListeners) {
                        l.treeNodesInserted(e);
                    }
                }
            }
        });
    }

    void notifyTreeStructureUpdate(final TreeModelEvent e) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                synchronized (treeModelListeners) {
                    for (final TreeModelListener l : treeModelListeners) {
                        l.treeStructureChanged(e);
                    }
                }
            }
        });
    }

    void notifyTreeNodeChanged(final TreeModelEvent e) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                synchronized (treeModelListeners) {
                    for (final TreeModelListener l : treeModelListeners) {
                        l.treeNodesChanged(e);
                    }
                }
            }
        });
    }

    @Override
    public void onDeviceFound() {
        log.info("Connected to device");
        notifyConnectionListners(true);

        if (ftpClient == null) {
            ftpClient = new FtpScanner(this, dlnaClient.getRemoteHostname());
            ftpClient.addDeviceListener(this);
        }

        scanTask = scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                log.debug("Scheduled scan");
                triggerScan();
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    @Override
    public void onDeviceLost() {
        if (scanTask != null) {
            scanTask.cancel(true);
        }
        log.info("Disconnected from device");
        notifyConnectionListners(false);
        if (ftpClient != null) {
            ftpClient.stop();
            ftpClient = null;
        }
        treeWalk(new TreeWalker() {
            @Override
            public void action(PVRItem item, Iterator it) {
                if (item.isFile()) {
                    PVRFile file = (PVRFile) item;
                    file.setDlnaScanned(false);
                }
            }
        }, true);
    }

    @Override
    public void onScanStart(ScanType type) {
        notifyScanListeners(type, true);
    }

    public void triggerScan() {
        if (ftpClient != null) {
            ftpClient.start();
        } else {
            log.warn("No FTP client, not scanning");
        }
    }

    void updateItem(PVRItem item) {
        DownloadManager.getInstance().addIfSaved(item);
            
        

        item.setLastScanned(System.currentTimeMillis());

        while (!item.equals(rootFolder)) {
            notifyTreeNodeChanged(new TreeModelEvent(this, item.getParent().getTreePath(), new int[]{item.getParent().getChildIndex(item)}, new Object[]{item}));
            item = item.getParent();
        }
        notifyTreeNodeChanged(new TreeModelEvent(this, item.getTreePath(), null, null));
    }

    @Override
    public void onScanProgress(ScanType type, int total, int completed) {
        notifyScanisteners(type, total, completed);
    }

    @Override
    public void onScanComplete(ScanType type) {
        notifyScanListeners(type, false);
        if (running.get() && type == ScanType.ftp) {
            dlnaClient.startScan();
        } else if (running.get() && type == ScanType.dlna) {
            removeStaleItems(5 * 60 * 1000);
        }
    }

    private final Set<DeviceListener> deviceListener = new HashSet<>();


    public void addDeviceListener(DeviceListener l) {
        synchronized (deviceListener) {
            deviceListener.add(l);
        }
    }

    public void removeDeviceListener(DeviceListener l) {
        synchronized (deviceListener) {
            deviceListener.remove(l);
        }
    }

    private void notifyScanListeners(ScanType type, boolean startStop) {
        synchronized (deviceListener) {
            for (DeviceListener l : deviceListener) {
                if (startStop) {
                    l.onScanStart(type);
                } else {
                    l.onScanComplete(type);
                }
            }
        }
    }

    private void notifyScanisteners(ScanType type, int total, int completed) {
        synchronized (deviceListener) {
            for (DeviceListener l : deviceListener) {
                l.onScanProgress(type, total, completed);
            }
        }
    }

    private void notifyConnectionListners(boolean connect) {
        synchronized (deviceListener) {
            for (DeviceListener l : deviceListener) {
                if (connect) {
                    l.onDeviceFound();
                } else {
                    l.onDeviceLost();

                }
            }
        }
    }

    public interface TreeWalker {

        public void action(PVRItem item, Iterator it);
    }
    

}

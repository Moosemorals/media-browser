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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final PVRFolder rootFolder = new PVRFolder(null, "/", "");
    private final AtomicBoolean running;
    private final UpnpScanner upnpClient;
    private final Preferences prefs;
    private FtpScanner ftpClient;
    private final Map<String, String> savedPaths;

    PVR(Preferences prefs) {
        this.prefs = prefs;
        upnpClient = new UpnpScanner(this);
        running = new AtomicBoolean(false);

        if (isSavingPaths()) {
            log.debug("Loading saved paths");
            savedPaths = getSavedPaths();
            clearSavedPaths();
        } else {
            savedPaths = null;
        }

        prefs.addPreferenceChangeListener(new PreferenceChangeListener() {
            @Override
            public void preferenceChange(PreferenceChangeEvent evt) {
                if (evt.getKey().equals(KEY_SAVE_DOWNLOAD_LIST) && evt.getNewValue().equals("false")) {
                    clearSavedPaths();
                }
            }
        });
    }

    @Override
    public Object getRoot() {
        return rootFolder;
    }

    @Override
    public Object getChild(Object parent, int index) {
        synchronized (((PVRFolder) parent).children) {
            return ((PVRFolder) parent).getChild(index);
        }
    }

    @Override
    public int getChildCount(Object parent) {
        synchronized (((PVRFolder) parent).children) {
            return ((PVRFolder) parent).getChildCount();
        }
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
    public int getIndexOfChild(Object parent, Object child) {
        synchronized (((PVRFolder) parent).children) {
            if (parent != null && parent instanceof PVRFolder && child != null && child instanceof PVRItem) {
                for (int i = 0; i < ((PVRFolder) parent).getChildCount(); i += 1) {
                    if ((child.equals(((PVRFolder) parent).getChild(i)))) {
                        return i;
                    }
                }
            }
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

            upnpClient.addDeviceListener(this);
            upnpClient.startSearch();
        }
    }

    /**
     * Stop any running threads and tidy up.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.debug("Stopping");
            upnpClient.stop();
            if (ftpClient != null) {
                ftpClient.stop();
            }

            rootFolder.clearChildren();
            notifyTreeStructureUpdate(new TreeModelEvent(this, rootFolder.getTreePath()));

            if (isSavingPaths()) {
                log.debug("Saving paths");
                savePaths();
            }
        }
    }

    public void treeWalk(TreeWalker walker) {
        treeWalk(rootFolder, walker);
    }

    private void treeWalk(PVRFolder target, TreeWalker walker) {
        for (PVRItem child : target.children) {
            walker.action(child);
            if (child.isFolder()) {
                treeWalk((PVRFolder) child, walker);
            }
        }
    }

    public void unlockFile(List<PVRFile> files) throws IOException {
        if (ftpClient != null) {
            ftpClient.unlockFile(files);
        }
    }

    public void rename(List<PVRFile> files) throws IOException {
        if (ftpClient != null) {
            ftpClient.renameFile(files);
        }
    }

    /**
     * Adds a Folder to the tree. Will return an existing folder if it can.
     *
     * @param parent {@link PVRFolder} parent, must not be null.
     * @param folderName String PVRs name of the folder.
     * @return {@link PVRFolder} either an existing Folder, or a new one.
     */
    PVRFolder addFolder(PVRFolder parent, String folderName) {
        synchronized (parent.children) {
            for (PVRItem child : parent.children) {
                if (child.getRemoteFilename().equals(folderName)) {
                    if (child.isFolder()) {
                        return (PVRFolder) child;
                    } else {
                        throw new RuntimeException("Can't add file [" + folderName + "] to " + parent.getRemotePath() + ": Already exists as folder");
                    }
                }
            }

            PVRFolder folder = new PVRFolder(parent, parent.getRemotePath() + folderName + "/", folderName);
            parent.addChild(folder);

            notifyTreeNodeInserted(new TreeModelEvent(this, parent.getTreePath(), new int[]{parent.getChildIndex(folder)}, new Object[]{folder}));
            notifyTreeStructureUpdate(new TreeModelEvent(this, parent.getTreePath()));
            return folder;
        }
    }

    PVRFile addFile(PVRFolder parent, String filename) {
        synchronized (parent.children) {
            for (PVRItem child : parent.children) {
                if (child.getRemoteFilename().equals(filename)) {
                    if (child.isFile()) {
                        return (PVRFile) child;
                    } else {
                        throw new RuntimeException("Can't add folder [" + filename + "] to " + parent.getRemotePath() + ": Already exists as file");
                    }
                }
            }

            PVRFile file = new PVRFile(parent, parent.getRemotePath() + filename, filename);
            parent.addChild(file);

            notifyTreeNodeInserted(new TreeModelEvent(this, parent.getTreePath(), new int[]{parent.getChildIndex(file)}, new Object[]{file}));
            notifyTreeStructureUpdate(new TreeModelEvent(this, parent.getTreePath()));
            return file;
        }
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
    }

    @Override
    public void onDeviceLost() {
        log.info("Disconnected from device");
        notifyConnectionListners(false);
        if (ftpClient != null) {
            ftpClient.stop();
            ftpClient = null;
        }
    }

    @Override
    public void onScanStart(ScanType type) {
        notifyScanListeners(type, true);
    }

    void updateItem(PVRItem item) {
        if (item.isFile()) {
            PVRFile file = (PVRFile) item;
            String remotePath = file.getRemotePath();
            if (savedPaths.containsKey(item.getRemotePath())) {
                DownloadManager.getInstance().add(file, savedPaths.get(remotePath));
            }
        }

        while (!item.equals(rootFolder)) {
            notifyTreeNodeChanged(new TreeModelEvent(this, item.getParent().getTreePath(), new int[]{item.getParent().getChildIndex(item)}, new Object[]{item}));
            item = item.getParent();
        }
    }

    @Override
    public void onScanProgress(ScanType type, int total, int completed) {
        notifyScanisteners(type, total, completed);
    }

    @Override
    public void onScanComplete(ScanType type) {
        notifyScanListeners(type, false);
        if (running.get() && type == ScanType.upnp) {
            ftpClient = new FtpScanner(this, upnpClient.getRemoteHostname());
            ftpClient.addDeviceListener(this);
            ftpClient.start();
        }
    }

    private final Set<DeviceListener> deviceListener = new HashSet<>();

    private boolean isSavingPaths() {
        return prefs.getBoolean(KEY_SAVE_DOWNLOAD_LIST, false);
    }

    private Map<String, String> getSavedPaths() {
        final Map<String, String> result = new HashMap<>();

        if (isSavingPaths()) {
            int count = prefs.getInt(KEY_SAVE_DOWNLOAD_COUNT, -1);
            for (int i = 0; i < count; i += 1) {
                String remotePath = prefs.get(KEY_SAVE_DOWNLOAD_REMOTE + i, null);
                String localPath = prefs.get(KEY_SAVE_DOWNLOAD_LOCAL + i, null);
                if (remotePath != null && localPath != null) {
                    result.put(remotePath, localPath);
                }
            }
        }
        log.debug("Saved paths: {}", result);
        return result;
    }

    private void clearSavedPaths() {
        int count = prefs.getInt(KEY_SAVE_DOWNLOAD_COUNT, -1);
        for (int i = 0; i < count; i += 1) {
            prefs.remove(KEY_SAVE_DOWNLOAD_REMOTE + i);
            prefs.remove(KEY_SAVE_DOWNLOAD_LOCAL + i);
        }
        prefs.remove(KEY_SAVE_DOWNLOAD_COUNT);
    }

    private void savePaths() {
        List<DownloadManager.QueueItem> queue = DownloadManager.getInstance().getQueue();

        int count = 0;

        for (DownloadManager.QueueItem item : queue) {
            PVRFile file = item.getTarget();
            if (item.getState() == DownloadManager.QueueItem.State.Downloading || item.getState() == DownloadManager.QueueItem.State.Paused || item.getState() == DownloadManager.QueueItem.State.Queued) {
                log.debug("Saving {} {} {}", count, item.getLocalPath().getPath(), file.getRemotePath());
                prefs.put(KEY_SAVE_DOWNLOAD_LOCAL + count, item.getLocalPath().getPath());
                prefs.put(KEY_SAVE_DOWNLOAD_REMOTE + count, file.getRemotePath());
                count += 1;
            }
        }
        prefs.putInt(KEY_SAVE_DOWNLOAD_COUNT, count);
    }

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

        public void action(PVRItem item);
    }
}

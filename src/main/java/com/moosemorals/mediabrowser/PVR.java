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
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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
        } else {
            return String.format("%db", size);
        }
    }

    private final Logger log = LoggerFactory.getLogger(PVR.class);
    private final Set<TreeModelListener> treeModelListeners = new HashSet<>();
    private final PVRFolder rootFolder = new PVRFolder(null, "/", "");
    private final AtomicBoolean running;
    private final UpnpScanner upnpClient;
    private FtpScanner ftpClient;

    PVR() {
        upnpClient = new UpnpScanner(this);
        running = new AtomicBoolean(false);
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
            upnpClient.stop();
            if (ftpClient != null) {
                ftpClient.stop();
            }

            rootFolder.clearChildren();
            notifyListenersUpdate(new TreeModelEvent(this, rootFolder.getTreePath()));
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

    public void unlockFile(PVRFile file) throws IOException {
        if (ftpClient != null) {
            ftpClient.unlockFile(file);
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

            onUpdateItem(folder);
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

            onUpdateItem(file);
            return file;
        }
    }

    void notifyListenersUpdate(final TreeModelEvent e) {
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
    public void onBrowseBegin(BrowseType type) {
        log.info("Browse for {} started", type);
        notifyBrowseListeners(type, true);
    }

    void onUpdateItem(PVRItem item) {
        notifyListenersUpdate(new TreeModelEvent(this, item.getParent().getTreePath()));
    }

    @Override
    public void onBrowseEnd(BrowseType type) {
        log.info("Browse for {} completed", type);
        TreeModelEvent e = new TreeModelEvent(this, rootFolder.getTreePath());
        notifyListenersUpdate(e);
        if (running.get() && type == BrowseType.upnp) {
            ftpClient = new FtpScanner(this, upnpClient.getRemoteHostname());
            ftpClient.addDeviceListener(this);
            ftpClient.start();
        }
        notifyBrowseListeners(type, false);
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

    private void notifyBrowseListeners(BrowseType type, boolean startStop) {
        synchronized (deviceListener) {
            for (DeviceListener l : deviceListener) {
                if (startStop) {
                    l.onBrowseBegin(type);
                } else {
                    l.onBrowseEnd(type);
                }
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

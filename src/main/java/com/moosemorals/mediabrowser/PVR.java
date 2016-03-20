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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.message.header.STAllHeader;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.support.contentdirectory.callback.Browse;
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
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
public class PVR implements TreeModel {

    private static final String DEVICE_NAME = "HUMAX HDR-FOX T2 Undefine";
    private static final String FTP_ROOT = "/My Video/";

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

    private final boolean debugFTP = false;

    private final Logger log = LoggerFactory.getLogger(PVR.class);
    private final Set<TreeModelListener> treeModelListeners = new HashSet<>();
    private final PVRFolder rootFolder = new PVRFolder(null, "", "/");
    private final FTPClient ftp;
    private final Object flag = new Object();
    private final UpnpService upnp;
    private final List<DeviceBrowse> upnpQueue;
    private final AtomicBoolean upnpRunning;
    private final AtomicBoolean ftpRunning;
    private final Set<PVRListener> connectionListeners;

    private Thread upnpThread = null;
    private Thread ftpThread = null;
    private String remoteHostname = null;
    private final DefaultRegistryListener upnpListener = new DefaultRegistryListener() {

        private RemoteDevice connectedDevice = null;

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            if (DEVICE_NAME.equals(device.getDisplayString())) {
                log.info("Connected to {}", DEVICE_NAME);
                onConnect(device);
                connectedDevice = device;

            }
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            if (device.equals(connectedDevice)) {
                log.info("Disconnected from {}", DEVICE_NAME);
                onDisconnect(device);
                connectedDevice = null;
            }
        }
    };

    PVR() {
        connectionListeners = new HashSet<>();

        FTPClientConfig config = new FTPClientConfig();
        config.setServerTimeZoneId(DEFAULT_TIMEZONE.getID());
        config.setServerLanguageCode("EN");

        ftp = new FTPClient();
        ftp.configure(config);
        if (debugFTP) {
            ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out), true));
        }
        ftpRunning = new AtomicBoolean(false);

        upnp = new UpnpServiceImpl(upnpListener);
        upnpQueue = new ArrayList<>();
        upnpRunning = new AtomicBoolean(false);

    }

    private void setRemoteHostname(String remoteHostname) {
        this.remoteHostname = remoteHostname;
    }

    private String getRemoteHostname() {
        return remoteHostname;
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

    public void addConnectionListener(PVRListener l) {
        synchronized (connectionListeners) {
            connectionListeners.add(l);
        }
    }

    public void removeConnectionListener(PVRListener l) {
        synchronized (connectionListeners) {
            connectionListeners.add(l);
        }
    }

    /**
     * Unset the locked flag.
     *
     * <p>
     * This is one of the two core operations of this project. It connects to
     * the PVR using FTP, fetches the PVR's control file, sets the value of a
     * significant byte and uploads the file back to the PVR, over-writing the
     * existing file.</p>
     *
     * <p>
     * Using this method may be illegal in certain jurisdictions. Seriously. See
     * <a href="https://en.wikipedia.org/wiki/Anti-circumvention">this Wikipedia
     * article</a> to start with, and then talk to a lawyer.</p>
     *
     * @param target
     * @throws IOException
     */
    public void unlockFile(PVRFile target) throws IOException {

        if (!target.getRemoteFilename().endsWith(".ts")) {
            throw new IllegalArgumentException("Target must be a .ts file: " + target.getRemoteFilename());
        }

        log.info("Connecting to FTP");

        ftp.connect(getRemoteHostname());
        int reply = ftp.getReplyCode();

        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            throw new IOException("FTP server refused connect");
        }

        if (!ftp.login("humaxftp", "0000")) {
            throw new IOException("Can't login to FTP");
        }

        if (!ftp.setFileType(FTPClient.BINARY_FILE_TYPE)) {
            throw new IOException("Can't set binary transfer");
        }

        if (!ftp.changeWorkingDirectory(FTP_ROOT + target.getParent().getRemotePath())) {
            throw new IOException("Can't change FTP directory to " + FTP_ROOT + target.getParent().getRemotePath());
        }

        HMTFile hmt = getHMTForTs(target);

        if (!hmt.isLocked()) {
            log.info("Unlock failed: {} is already unlocked", target.getRemoteFilename());
            return;
        }

        hmt.clearLock();

        String uploadFilename = target.getRemoteFilename().replaceAll("\\.ts$", ".hmt");

        log.info("Uploading unlocked hmt file to {}/{}", target.getParent().getRemotePath(), uploadFilename);
        if (!ftp.storeFile(uploadFilename, new ByteArrayInputStream(hmt.getBytes()))) {
            throw new IOException("Can't upload unlocked hmt to " + uploadFilename);
        }

        ftp.disconnect();
        log.info("Disconnected from FTP");
    }

    /**
     * Starts looking for devices to connect to. Starts a new thread and then
     * returns. New devices will be reported asynchronously..
     */
    public void start() {
        upnp.getControlPoint().search(new STAllHeader());
    }

    /**
     * Stop any running threads and tidy up.
     */
    public void stop() {
        upnp.shutdown();

        stopUpnp();
        stopFTP();

        rootFolder.clearChildren();
        notifyListenersUpdate(new TreeModelEvent(this, rootFolder.getTreePath()));
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

    private void onConnect(RemoteDevice device) {
        setRemoteHostname(device.getIdentity().getDescriptorURL().getHost());
        Service service = device.findService(new UDAServiceType("ContentDirectory"));
        if (service != null) {
            synchronized (upnpQueue) {
                upnpQueue.add(new DeviceBrowse(service, "0\\1\\2", rootFolder));
                upnpQueue.notifyAll();
            }
            startUPNP();
        }
        notifyConnectionListeners(true);
    }

    private void onDisconnect(RemoteDevice device) {
        stopUpnp();
        stopFTP();
        rootFolder.clearChildren();
        notifyListenersUpdate(new TreeModelEvent(this, rootFolder.getTreePath()));
        notifyConnectionListeners(false);
    }

    /**
     * Adds a Folder to the tree. Will return an existing folder if it can.
     *
     * @param parent {@link PVRFolder} parent, must not be null.
     * @param folderName String PVRs name of the folder.
     * @return {@link PVRFolder} either an existing Folder, or a new one.
     */
    private PVRFolder addFolder(PVRFolder parent, String folderName) {
        synchronized (parent.children) {
            for (PVRItem child : parent.children) {
                if (child.getRemoteFilename().equals(folderName)) {
                    if (child.isFolder()) {
                        return (PVRFolder) child;
                    } else {
                        throw new RuntimeException("Can't add file [" + folderName + "] to " + parent.remotePath + ": Already exists as folder");
                    }
                }
            }

            PVRFolder folder = new PVRFolder(parent, parent.getRemotePath() + folderName + "/", folderName);
            parent.addChild(folder);

            notifyListenersUpdate(new TreeModelEvent(this, parent.getTreePath()));
            return folder;
        }
    }

    private PVRFile addFile(PVRFolder parent, String filename) {
        synchronized (parent.children) {
            for (PVRItem child : parent.children) {
                if (child.getRemoteFilename().equals(filename)) {
                    if (child.isFile()) {
                        return (PVRFile) child;
                    } else {
                        throw new RuntimeException("Can't add folder [" + filename + "] to " + parent.remotePath + ": Already exists as file");
                    }
                }
            }

            PVRFile file = new PVRFile(parent, parent.getRemotePath() + filename, filename);
            parent.addChild(file);

            notifyListenersUpdate(new TreeModelEvent(this, parent.getTreePath()));
            return file;
        }
    }

    private void notifyListenersUpdate(final TreeModelEvent e) {
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

    private void notifyConnectionListeners(boolean connected) {
        synchronized (connectionListeners) {
            for (PVRListener l : connectionListeners) {
                if (connected) {
                    l.onConnect();
                } else {
                    l.onDisconnect();
                }
            }
        }
    }

    private void notifyBrowseListeners(BrowseType type, boolean start) {
        synchronized (connectionListeners) {
            for (PVRListener l : connectionListeners) {
                l.onBrowse(type, start);
            }
        }
    }

    private HMTFile getHMTForTs(PVRFile file) throws IOException {
        String target = file.getRemoteFilename().replaceAll("\\.ts$", ".hmt");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if (!ftp.retrieveFile(target, out)) {
            throw new IOException("Can't download " + file.getRemotePath() + ": Unknown reason");
        }
        return new HMTFile(out.toByteArray());
    }

    private void startUPNP() {
        if (upnpRunning.compareAndSet(false, true)) {
            upnpThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    DeviceBrowse next;
                    try {
                        while (upnpRunning.get()) {

                            synchronized (upnpQueue) {
                                while (upnpQueue.isEmpty()) {
                                    upnpQueue.wait();
                                }
                                next = upnpQueue.remove(0);
                            }

                            upnp.getControlPoint().execute(next);
                            synchronized (flag) {
                                flag.wait();
                            }

                            synchronized (upnpQueue) {
                                if (upnpQueue.isEmpty()) {
                                    log.info("Browse complete");
                                    startFTP();
                                    return;
                                }
                            }
                        }
                    } catch (InterruptedException ex) {
                        log.error("IOException in upnp thread: {}", ex.getMessage(), ex);
                        return;
                    } finally {
                        upnpRunning.set(false);
                        notifyBrowseListeners(BrowseType.upnp, false);
                    }
                }
            }, "UPNP");
            upnpThread.start();
            notifyBrowseListeners(BrowseType.upnp, true);

        }
    }

    private void stopUpnp() {
        if (upnpRunning.compareAndSet(true, false) && upnpThread != null) {
            upnpThread.interrupt();
            log.info("Waiting for upnpThread to finish");
            try {
                upnpThread.join();
            } catch (InterruptedException ex) {
                log.error("Unexpected interruption waiting for upnpThread, ignored");
            }
        }
    }

    private void startFTP() {
        if (ftpRunning.compareAndSet(false, true)) {
            ftpThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        scrapeFTP();
                    } catch (IOException ex) {
                        log.error("FTP problem: {}", ex.getMessage(), ex);
                        stopFTP();
                    } finally {
                        TreeModelEvent e = new TreeModelEvent(this, rootFolder.getTreePath());
                        notifyListenersUpdate(e);
                        notifyBrowseListeners(BrowseType.ftp, false);
                        ftpRunning.set(false);
                    }
                }
            }, "FTP");
            ftpThread.start();
            notifyBrowseListeners(BrowseType.ftp, true);

        }
    }

    private void scrapeFTP() throws IOException {
        log.info("Connecting to FTP");

        ftp.connect(getRemoteHostname());
        int reply = ftp.getReplyCode();

        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            throw new IOException("FTP server refused connect");
        }
        if (!ftp.login("humaxftp", "0000")) {
            throw new IOException("Can't login to FTP");
        }

        if (!ftp.setFileType(FTPClient.BINARY_FILE_TYPE)) {
            throw new IOException("Can't set binary transfer");
        }

        List<PVRFolder> queue = new ArrayList<>();

        queue.add((PVRFolder) getRoot());

        while (!queue.isEmpty()) {
            PVRFolder directory = queue.remove(0);

            if (!ftp.changeWorkingDirectory(FTP_ROOT + directory.getRemotePath())) {
                throw new IOException("Can't change FTP directory to " + FTP_ROOT + directory.getRemotePath());
            }

            for (FTPFile f : ftp.listFiles()) {
                if (f.getName().equals(".") || f.getName().equals("..")) {
                    // skip entries for this directory and parent directory
                    continue;
                }

                if (f.isDirectory()) {
                    PVRFolder next = addFolder(directory, f.getName());
                    queue.add(next);
                } else if (f.isFile() && f.getName().endsWith(".ts")) {
                    PVRFile file = addFile(directory, f.getName());
                    HMTFile hmt = getHMTForTs(file);
                    file.setSize(f.getSize());
                    file.setDescription(hmt.getDesc());
                    file.setTitle(hmt.getRecordingTitle());
                    file.setStartTime(new DateTime(hmt.getStartTimestamp() * 1000, DEFAULT_TIMEZONE));
                    file.setEndTime(new DateTime(hmt.getEndTimestamp() * 1000, DEFAULT_TIMEZONE));
                    file.setLength(new Duration(hmt.getLength() * 1000));
                    file.setHighDef(hmt.isHighDef());
                    file.setLocked(hmt.isLocked());
                    file.setChannelName(hmt.getChannelName());
                    file.setFtp(true);

                    file.setLocalFilename(String.format("%s - %s - [%s - Freeview - %s] UNEDITED",
                            file.getTitle().replaceAll("[/?<>\\:*|\"^]", "_"),
                            FILE_DATE_FORMAT.print(file.getStartTime()),
                            file.isHighDef() ? "1920×1080" : "SD",
                            file.getChannelName()
                    ));
                }
            }
        }
        ftp.disconnect();
        log.info("Disconnected from FTP");
    }

    private void stopFTP() {
        if (ftpRunning.compareAndSet(true, false) && ftpThread != null) {
            ftpThread.interrupt();
            try {
                log.info("Waitng for ftpThread to finish");
                ftpThread.join();
            } catch (InterruptedException ex) {
                log.error("Unexpected interruption waiting for ftpThread to finish");
            }

        }
    }

    /**
     * Part of the Cing framework. This class implements the upnp device search
     * stuff.
     */
    private class DeviceBrowse extends Browse {

        private final PVRFolder parent;
        private final Service service;

        DeviceBrowse(Service service, String id, PVRFolder parent) {
            super(service, id, BrowseFlag.DIRECT_CHILDREN);
            this.parent = parent;
            this.service = service;
        }

        @Override
        public void received(ActionInvocation actionInvocation, DIDLContent didl) {
            List<Container> containers = didl.getContainers();

            for (Container c : containers) {
                PVRFolder folder = addFolder(parent, c.getTitle());
                synchronized (upnpQueue) {
                    upnpQueue.add(new DeviceBrowse(service, c.getId(), folder));
                    upnpQueue.notifyAll();
                }
            }
            List<Item> items = didl.getItems();

            for (Item i : items) {
                PVRFile file = addFile(parent, i.getTitle());

                file.setUpnp(true);

                Res res = i.getFirstResource();
                if (res != null) {
                    file.setRemoteURL(res.getValue());
                }

            }
            synchronized (flag) {
                flag.notifyAll();
            }
        }

        @Override
        public void updateStatus(Browse.Status status) {

        }

        @Override
        public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
            synchronized (flag) {
                flag.notifyAll();
            }
        }
    }

    /**
     * Used to indicate what type of browsing we're doing.
     */
    public enum BrowseType {
        upnp, ftp
    };

    /**
     * For objects that want to know whats happening with this PVR.
     */
    public interface PVRListener {

        /**
         * Called when we connect to a PVR.
         */
        public void onConnect();

        /**
         * Called when we disconnect from a PVR.
         */
        public void onDisconnect();

        /**
         * Called when we start or stop browsing the PVR.
         *
         * <p>
         * We can browse in two ways, through upnp (DLNA) or through FTP.
         * Mostly, upnp starts first, and then once we've located a device
         * through upnp, we start browsing through FTP.
         *
         * @param type {@link BrowseType} indicating if we're talking about upnp
         * or ftp.
         * @param startStop boolean true for start, false for stop..
         */
        public void onBrowse(BrowseType type, boolean startStop);
    }

    public interface TreeWalker {

        public void action(PVRItem item);
    }
}

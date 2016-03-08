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
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
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
 * PVRs have folders that contain files. Files have a bunch of attributes.
 * (Folders also have attributes
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
class PVR implements TreeModel {

    private static final String DEVICE_NAME = "HUMAX HDR-FOX T2 Undefine";
    private static final String FTP_ROOT = "/My Video/";

    static final DateTimeZone DEFAULT_TIMEZONE = DateTimeZone.forID("Europe/London");
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern("YYYY-MM-dd'T'HH-mm").withZone(DEFAULT_TIMEZONE);

    static final PeriodFormatter PERIOD_FORMAT = new PeriodFormatterBuilder()
            .appendHours()
            .appendSeparatorIfFieldsBefore("h")
            .appendMinutes()
            .appendSeparatorIfFieldsBefore("m")
            .appendSeconds()
            .appendSeparatorIfFieldsBefore("s")
            .toFormatter();

    static final double KILO = 1024;
    static final double MEGA = KILO * 1024;
    static final double GIGA = MEGA * 1024;
    static final double TERA = GIGA * 1024;

    private static final String SIZE_FORMAT = "% 6.1f %sb";

    static String humanReadableSize(long size) {
        if (size > TERA) {
            return String.format(SIZE_FORMAT, size / TERA, "T");
        } else if (size > GIGA) {
            return String.format(SIZE_FORMAT, size / GIGA, "G");
        } else if (size > MEGA) {
            return String.format(SIZE_FORMAT, size / MEGA, "M");
        } else if (size > KILO) {
            return String.format(SIZE_FORMAT, size / MEGA, "k");
        } else {
            return String.format("% 5db", size);
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
    private final Set<ConnectionListener> connectionListeners;

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

    void setRemoteHostname(String remoteHostname) {
        this.remoteHostname = remoteHostname;
    }

    String getRemoteHostname() {
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

    void onConnect(RemoteDevice device) {
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

    void onDisconnect(RemoteDevice device) {
        stopUpnp();
        stopFTP();
        rootFolder.clearChildren();
        notifyListenersUpdate(new TreeModelEvent(this, rootFolder.getTreePath()));
        notifyConnectionListeners(false);
    }

    PVRFolder addFolder(PVRFolder parent, String folderName) {
        synchronized (parent.children) {
            for (PVRItem child : parent.children) {
                if (child.getFilename().equals(folderName)) {
                    if (child.isFolder()) {
                        return (PVRFolder) child;
                    } else {
                        throw new RuntimeException("Can't add file [" + folderName + "] to " + parent.path + ": Already exists as folder");
                    }
                }
            }

            PVRFolder folder = new PVRFolder(parent, parent.getPath() + folderName + "/", folderName);
            parent.addChild(folder);

            notifyListenersUpdate(new TreeModelEvent(this, parent.getTreePath()));
            return folder;
        }
    }

    PVRFile addFile(PVRFolder parent, String filename) {
        synchronized (parent.children) {
            for (PVRItem child : parent.children) {
                if (child.getFilename().equals(filename)) {
                    if (child.isFile()) {
                        return (PVRFile) child;
                    } else {
                        throw new RuntimeException("Can't add folder [" + filename + "] to " + parent.path + ": Already exists as file");
                    }
                }
            }

            PVRFile file = new PVRFile(parent, parent.getPath() + filename, filename);
            parent.addChild(file);

            notifyListenersUpdate(new TreeModelEvent(this, parent.getTreePath()));
            return file;
        }
    }

    void dumpTree() {
        dumpTree(rootFolder);
    }

    void dumpTree(PVRFolder folder) {
        synchronized (folder.children) {
            for (PVRItem child : folder.children) {
                if (child.isFolder()) {
                    log.debug("Folder : [{}] - [{}]", child.getPath(), child.getFilename());
                    dumpTree((PVRFolder) child);
                } else {
                    log.debug("File   : [{}] - [{}] - [{}]", child.getPath(), child.getFilename(), ((PVRFile) child).getRemoteURL());
                }
            }
        }
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

    void addConnectionListener(ConnectionListener l) {
        synchronized (connectionListeners) {
            connectionListeners.add(l);
        }
    }

    void removeConnectionListener(ConnectionListener l) {
        synchronized (connectionListeners) {
            connectionListeners.add(l);
        }
    }

    private void notifyConnectionListeners(boolean connected) {
        synchronized (connectionListeners) {
            for (ConnectionListener l : connectionListeners) {
                if (connected) {
                    l.onConnect();
                } else {
                    l.onDisconnect();
                }
            }
        }
    }

    void unlockFile(PVRFile target) throws IOException {

        if (!target.getFilename().endsWith(".ts")) {
            throw new IllegalArgumentException("Target must be a .ts file: " + target.getFilename());
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

        if (!ftp.changeWorkingDirectory(FTP_ROOT + target.getParent().getPath())) {
            throw new IOException("Can't change FTP directory to " + FTP_ROOT + target.getParent().getPath());
        }

        HMTFile hmt = getHMTForTs(target);

        if (!hmt.isLocked()) {
            log.info("Unlock failed: {} is already unlocked", target.getFilename());
            return;
        }

        hmt.clearLock();

        String uploadFilename = target.getFilename().replaceAll("\\.ts$", ".hmt");

        log.info("Uploading unlocked hmt file to {}/{}", target.getParent().getPath(), uploadFilename);
        if (!ftp.storeFile(uploadFilename, new ByteArrayInputStream(hmt.getBytes()))) {
            throw new IOException("Can't upload unlocked hmt to " + uploadFilename);
        }

        ftp.disconnect();
        log.info("Disconnected from FTP");
    }

    private HMTFile getHMTForTs(PVRFile file) throws IOException {
        String target = file.getFilename().replaceAll("\\.ts$", ".hmt");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if (!ftp.retrieveFile(target, out)) {
            throw new IOException("Can't download " + file.getPath() + ": Unknown reason");
        }

        return new HMTFile(out.toByteArray());
    }

    void start() {
        upnp.getControlPoint().search(new STAllHeader());
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
                        return;
                    } finally {
                        upnpRunning.set(false);
                    }
                }
            }, "UPNP");
            upnpThread.start();
        }
    }

    private void stopUpnp() {
        if (upnpRunning.compareAndSet(true, false) && upnpThread != null) {
            upnpThread.interrupt();
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
                    }
                    TreeModelEvent e = new TreeModelEvent(this, rootFolder.getTreePath());
                    notifyListenersUpdate(e);
                }
            }, "FTP");
            ftpThread.start();
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

            if (!ftp.changeWorkingDirectory(FTP_ROOT + directory.getPath())) {
                throw new IOException("Can't change FTP directory to " + FTP_ROOT + directory.getPath());
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
                    file.setStart(new DateTime(hmt.getStartTimestamp() * 1000, DEFAULT_TIMEZONE));
                    file.setEnd(new DateTime(hmt.getEndTimestamp() * 1000, DEFAULT_TIMEZONE));
                    file.setLength(new Duration(hmt.getLength() * 1000));
                    file.setHighDef(hmt.isHighDef());
                    file.setLocked(hmt.isLocked());
                    file.setChannelName(hmt.getChannelName());

                    file.setDownloadFilename(String.format("%s - %s - [%s - Freeview - %s] UNEDITED",
                            file.getTitle().replaceAll("[/?<>\\:*|\"^]", "_"),
                            DATE_FORMAT.print(file.getStart()),
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
        }
    }

    void stop() {
        upnp.shutdown();

        stopUpnp();
        stopFTP();

        rootFolder.clearChildren();
        notifyListenersUpdate(new TreeModelEvent(this, rootFolder.getTreePath()));
    }

    static abstract class PVRItem implements Comparable<PVRItem> {

        protected final String filename;
        protected final String path;
        protected final PVRItem parent;
        protected final TreePath treePath;

        @SuppressWarnings("LeakingThisInConstructor")
        private PVRItem(PVRItem parent, String path, String filename) {
            this.parent = parent;
            this.filename = filename;
            this.path = path;
            if (parent != null) {
                Object[] parentPath = parent.getTreePath().getPath();
                Object[] myPath = new Object[parentPath.length + 1];
                System.arraycopy(parentPath, 0, myPath, 0, parentPath.length);
                myPath[myPath.length - 1] = this;
                treePath = new TreePath(myPath);
            } else {
                // root node;
                treePath = new TreePath(this);
            }
        }

        @Override
        public abstract int compareTo(PVRItem other);

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 89 * hash + Objects.hashCode(this.filename);
            hash = 89 * hash + Objects.hashCode(this.path);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final PVRItem other = (PVRItem) obj;
            return this.path.equals(other.path) && this.filename.equals(other.filename);
        }

        abstract boolean isFile();

        abstract boolean isFolder();

        abstract long getSize();

        String getFilename() {
            return filename;
        }

        String getPath() {
            return path;
        }

        PVRItem getParent() {
            return parent;
        }

        TreePath getTreePath() {
            return treePath;
        }

        @Override
        public String toString() {
            return filename;
        }
    }

    static class PVRFolder extends PVRItem {

        private final List<PVRItem> children;

        private PVRFolder(PVRItem parent, String path, String filename) {
            super(parent, path, filename);
            this.children = new ArrayList<>();
        }

        @Override
        public int compareTo(PVRItem o) {
            if (o.isFile()) {
                // Folders go first
                return -1;
            } else {
                return getFilename().compareTo(o.getFilename());
            }
        }

        @Override
        boolean isFolder() {
            return true;
        }

        @Override
        boolean isFile() {
            return false;
        }

        @Override
        long getSize() {
            synchronized (children) {
                long size = 0;
                for (PVRItem child : children) {
                    size += child.getSize();
                }
                return size;
            }
        }

        void addChild(PVRItem child) {
            synchronized (children) {
                children.add(child);
                Collections.sort(children);
            }
        }

        PVRItem getChild(int index) {
            synchronized (children) {
                return children.get(index);
            }
        }

        int getChildCount() {
            synchronized (children) {
                return children.size();
            }
        }

        void clearChildren() {
            synchronized (children) {
                for (Iterator<PVRItem> it = children.iterator(); it.hasNext();) {
                    PVRItem item = it.next();
                    if (item.isFolder()) {
                        ((PVRFolder) item).clearChildren();
                    }
                    it.remove();
                }
            }
        }
    }

    static class PVRFile extends PVRItem {

        private final Logger log = LoggerFactory.getLogger(PVRFile.class);

        private State state;
        private long size = -1;
        private long downloaded = -1;
        private String remoteURL = null;
        private String description = "";
        private String title = "";
        private String channelName = "Unknown";
        private File downloadPath = null;
        private String downloadFilename = null;
        private DateTime start;
        private DateTime end;
        private Duration length;
        private boolean highDef = false;
        private boolean locked = false;

        private PVRFile(PVRItem parent, String path, String filename) {
            super(parent, path, filename);
            state = State.Ready;
            title = filename;
            downloadFilename = filename;
        }

        @Override
        public int compareTo(PVRItem o) {
            if (o.isFolder()) {
                // Folders go first
                return 1;
            } else {
                int x = getFilename().compareTo(o.getFilename());
                if (x == 0) {
                    return start.compareTo(((PVRFile) o).start);
                } else {
                    return x;
                }
            }
        }

        @Override
        boolean isFolder() {
            return false;
        }

        @Override
        boolean isFile() {
            return true;
        }

        @Override
        long getSize() {
            return size;
        }

        void setState(State newState) {
            this.state = newState;
        }

        void setTitle(String title) {
            this.title = title;
        }

        String getTitle() {
            return title;
        }

        void setDownloadPath(File downloadPath) {
            this.downloadPath = downloadPath;
        }

        File getDownloadPath() {
            return downloadPath;
        }

        String getDownloadFilename() {
            return downloadFilename;
        }

        void setDownloadFilename(String downloadFilename) {
            this.downloadFilename = downloadFilename;
        }

        State getState() {
            return state;
        }

        void setSize(long size) {
            this.size = size;
        }

        long getDownloaded() {
            return downloaded;
        }

        void setDownloaded(long downloaded) {
            this.downloaded = downloaded;
        }

        String getDescription() {
            return description;
        }

        void setDescription(String description) {
            this.description = description;
        }

        String getRemoteURL() {
            return remoteURL;
        }

        void setRemoteURL(String remoteURL) {
            this.remoteURL = remoteURL;
        }

        DateTime getStart() {
            return start;
        }

        void setStart(DateTime start) {
            this.start = start;
        }

        DateTime getEnd() {
            return end;
        }

        void setEnd(DateTime end) {
            this.end = end;
        }

        Duration getLength() {
            return length;
        }

        void setLength(Duration length) {
            this.length = length;
        }

        boolean isHighDef() {
            return highDef;
        }

        void setHighDef(boolean highDef) {
            this.highDef = highDef;
        }

        boolean isLocked() {
            return locked;
        }

        void setLocked(boolean locked) {
            this.locked = locked;
        }

        String getChannelName() {
            return channelName;
        }

        void setChannelName(String channelName) {
            this.channelName = channelName;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();

            result.append(title);
            result.append(": ");
            switch (state) {
                case Ready:
                    result.append(PVR.humanReadableSize(size));
                    break;
                case Queued:
                    result.append(PVR.humanReadableSize(size)).append(" Queued");
                    break;
                case Downloading:
                    result.append(String.format("%s of %s (%3.0f%%) Downloading", PVR.humanReadableSize(downloaded), PVR.humanReadableSize(size), (downloaded / (double) size) * 100.0));
                    break;
                case Paused:
                    result.append(String.format("%s of %s (%3.0f%%) Paused", PVR.humanReadableSize(downloaded), PVR.humanReadableSize(size), (downloaded / (double) size) * 100.0));
                    break;
                case Completed:
                    result.append(PVR.humanReadableSize(downloaded)).append(" Completed");
                    break;
                case Error:
                    result.append(String.format("%s of %s (%3.0f%%) Broken", PVR.humanReadableSize(downloaded), PVR.humanReadableSize(size), (downloaded / (double) size) * 100.0));
                    break;
            }
            return result.toString();
        }

        static enum State {

            Ready, Queued, Downloading, Paused, Completed, Error
        }

    }

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

        }
    }

    interface ConnectionListener {

        void onConnect();

        void onDisconnect();
    }
}

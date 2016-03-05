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
package com.fluffypeople.pvrbrowser;

import java.awt.EventQueue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
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
 * PVRs have folders that contain files. Files have a bunch of attributes.
 * (Folders also have attributes
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
public class PVR implements TreeModel {

    private static final String DEVICE_NAME = "HUMAX HDR-FOX T2 Undefine";
    private static final String FTP_ROOT = "/My Video/";

    public static final DateTimeZone DEFAULT_TIMEZONE = DateTimeZone.forID("Europe/London");
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern("YYYY-MM-dd HH.mm").withZone(DEFAULT_TIMEZONE);

    public static final PeriodFormatter PERIOD_FORMAT = new PeriodFormatterBuilder()
            .appendHours()
            .appendSeparatorIfFieldsBefore("h")
            .appendMinutes()
            .appendSeparatorIfFieldsBefore("m")
            .appendSeconds()
            .appendSeparatorIfFieldsBefore("s")
            .toFormatter();

    public static final double KILO = 1024;
    public static final double MEGA = KILO * 1024;
    public static final double GIGA = MEGA * 1024;
    public static final double TERA = GIGA * 1024;

    private static final String SIZE_FORMAT = "% 6.1f %sb";

    public static String humanReadableSize(long size) {
        if (size > TERA) {
            return String.format(SIZE_FORMAT, (double) size / TERA, "T");
        } else if (size > GIGA) {
            return String.format(SIZE_FORMAT, (double) size / GIGA, "G");
        } else if (size > MEGA) {
            return String.format(SIZE_FORMAT, (double) size / MEGA, "M");
        } else if (size > KILO) {
            return String.format(SIZE_FORMAT, (double) size / MEGA, "k");
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
    private Thread upnpThread = null;
    private Thread ftpThread = null;
    private String remoteHostname = null;

    public PVR() {
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

    public void setRemoteHostname(String remoteHostname) {
        this.remoteHostname = remoteHostname;
    }

    public String getRemoteHostname() {
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

    public PVRFolder addFolder(PVRFolder parent, String folderName) {
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

    public PVRFile addFile(PVRFolder parent, String filename) {
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

    public void dumpTree() {
        dumpTree(rootFolder);
    }

    public void dumpTree(PVRFolder folder) {
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

    private void startFTP() {
        if (ftpRunning.compareAndSet(false, true)) {
            ftpThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        scrapeFTP();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
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
                }
            }
        }
        ftp.disconnect();
        log.info("Disconnected from FTP");
    }

    public void unlockFile(PVRFile target) throws IOException {

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

    private final DefaultRegistryListener upnpListener = new DefaultRegistryListener() {
        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            if (DEVICE_NAME.equals(device.getDisplayString())) {
                log.debug("Found {} in thread {}", DEVICE_NAME, Thread.currentThread().getName());
                setRemoteHostname(device.getIdentity().getDescriptorURL().getHost());
                populateTree(device);
            } else {
                log.info("Skiping device {} ", device.getDisplayString());
            }
        }
    };

    public void start() {
        if (upnpRunning.compareAndSet(false, true)) {
            upnp.getControlPoint().search(new STAllHeader());
            upnpThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    browseUpnp();
                }

            }, "BrowseQueue");

            upnpThread.start();
        }
    }

    public void stop() {
        if (upnpRunning.compareAndSet(true, false) && upnpThread != null) {
            upnpThread.interrupt();
        }

        if (ftpRunning.compareAndSet(false, true) && ftpThread != null) {
            ftpThread.interrupt();
        }

        upnp.shutdown();
    }

    private void populateTree(RemoteDevice device) {
        Service service = device.findService(new UDAServiceType("ContentDirectory"));
        if (service != null) {
            synchronized (upnpQueue) {
                upnpQueue.add(new DeviceBrowse(service, "0\\1\\2", (PVRFolder) getRoot()));
                upnpQueue.notifyAll();
            }
        }
    }

    private void browseUpnp() {
        while (upnpRunning.get()) {
            DeviceBrowse next;

            try {
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
                        upnpRunning.set(false);
                        break;
                    }
                }
            } catch (InterruptedException ex) {
                upnpRunning.set(false);
                return;
            }
        }
    }

    private class DeviceBrowse extends Browse {

        private final PVRFolder parent;
        private final Service service;

        public DeviceBrowse(Service service, String id, PVRFolder parent) {
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

    public static abstract class PVRItem implements Comparable<PVRItem> {

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

        public abstract boolean isFile();

        public abstract boolean isFolder();

        public abstract long getSize();

        public String getFilename() {
            return filename;
        }

        public String getPath() {
            return path;
        }

        public PVRItem getParent() {
            return parent;
        }

        public TreePath getTreePath() {
            return treePath;
        }

        @Override
        public String toString() {
            return filename;
        }
    }

    public static class PVRFolder extends PVRItem {

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
        public boolean isFolder() {
            return true;
        }

        @Override
        public boolean isFile() {
            return false;
        }

        @Override
        public long getSize() {
            synchronized (children) {
                long size = 0;
                for (PVRItem child : children) {
                    size += child.getSize();
                }
                return size;
            }
        }

        public void addChild(PVRItem child) {
            synchronized (children) {
                children.add(child);
                Collections.sort(children);
            }
        }

        public PVRItem getChild(int index) {
            synchronized (children) {
                return children.get(index);
            }
        }

        public int getChildCount() {
            synchronized (children) {
                return children.size();
            }
        }
    }

    public static class PVRFile extends PVRItem {

        private final Logger log = LoggerFactory.getLogger(PVRFile.class);

        public static enum State {
            Ready, Queued, Downloading, Paused, Completed, Error
        };

        private State state;
        private long size = -1;
        private long downloaded = -1;
        private String remoteURL = null;
        private String description = "";
        private String title = "";
        private String downloadPath = null;
        private DateTime start;
        private DateTime end;
        private Duration length;
        private boolean highDef = false;
        private boolean locked = false;

        private PVRFile(PVRItem parent, String path, String filename) {
            super(parent, path, filename);
            state = State.Ready;
            title = filename;
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

        public void setState(State newState) {
            this.state = newState;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        public void setDownloadPath(String downloadPath) {
            this.downloadPath = downloadPath;
        }

        public String getDownloadPath() {
            return downloadPath;
        }

        public State getState() {
            return state;
        }

        @Override
        public boolean isFolder() {
            return false;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public long getDownloaded() {
            return downloaded;
        }

        public void setDownloaded(long downloaded) {
            this.downloaded = downloaded;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getRemoteURL() {
            return remoteURL;
        }

        public void setRemoteURL(String remoteURL) {
            this.remoteURL = remoteURL;
        }

        public DateTime getStart() {
            return start;
        }

        public void setStart(DateTime start) {
            this.start = start;
        }

        public DateTime getEnd() {
            return end;
        }

        public void setEnd(DateTime end) {
            this.end = end;
        }

        public Duration getLength() {
            return length;
        }

        public void setLength(Duration length) {
            this.length = length;
        }

        public boolean isHighDef() {
            return highDef;
        }

        public void setHighDef(boolean highDef) {
            this.highDef = highDef;
        }

        public boolean isLocked() {
            return locked;
        }

        public void setLocked(boolean locked) {
            this.locked = locked;
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
                    result.append(String.format("%s of %s (%3.0f%%) Downloading", PVR.humanReadableSize(downloaded), PVR.humanReadableSize(size), ((double) downloaded / (double) size) * 100.0));
                    break;
                case Paused:
                    result.append(String.format("%s of %s (%3.0f%%) Paused", PVR.humanReadableSize(downloaded), PVR.humanReadableSize(size), ((double) downloaded / (double) size) * 100.0));
                    break;
                case Completed:
                    result.append(PVR.humanReadableSize(downloaded)).append(" Completed");
                    break;
                case Error:
                    result.append(String.format("%s of %s (%3.0f%%) Broken", PVR.humanReadableSize(downloaded), PVR.humanReadableSize(size), ((double) downloaded / (double) size) * 100.0));
                    break;
            }
            return result.toString();
        }

    }
}

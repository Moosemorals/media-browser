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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
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
public class PVR implements TreeModel, Runnable {

    private static final String DEVICE_NAME = "HUMAX HDR-FOX T2 Undefine";
    private static final String FTP_ROOT = "/My Video";

    private final Logger log = LoggerFactory.getLogger(PVR.class);
    private final Set<TreeModelListener> treeModelListeners = new HashSet<>();
    private final PVRFolder rootFolder = new PVRFolder(null, "", "/");
    private final FTPClient ftp;
    private final Object flag = new Object();
    private final UpnpService upnp;
    private final List<DeviceBrowse> upnpQueue;
    private final AtomicBoolean running;

    private String hostname = null;

    public PVR() {
        FTPClientConfig config = new FTPClientConfig();
        config.setServerTimeZoneId("Europe/London");
        config.setServerLanguageCode("EN");

        ftp = new FTPClient();
        ftp.configure(config);

        upnp = new UpnpServiceImpl(upnpListener);
        upnpQueue = new ArrayList<>();
        running = new AtomicBoolean(false);
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    scrapeFTP();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }, "FTPScrape").start();
    }

    public String getHostname() {
        return hostname;
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

    public PVRFolder addFolder(PVRFolder parent, String name) {
        synchronized (parent.children) {
            for (PVRItem child : parent.children) {
                if (child.getName().equals(name)) {
                    if (child.isFolder()) {
                        return (PVRFolder) child;
                    } else {
                        throw new RuntimeException("Can't add file [" + name + "] to " + parent.path + ": Already exists as folder");
                    }
                }
            }

            PVRFolder folder = new PVRFolder(parent, name, parent.getPath() + name + "/");
            parent.addChild(folder);

            notifyListeners(new TreeModelEvent(this, parent.getTreePath(), new int[]{parent.getChildCount() - 1}, new Object[]{folder}));
            return folder;
        }
    }

    public PVRFile addFile(PVRFolder parent, String name) {
        synchronized (parent.children) {
            for (PVRItem child : parent.children) {
                if (child.getName().equals(name)) {
                    if (child.isFile()) {
                        return (PVRFile) child;
                    } else {
                        throw new RuntimeException("Can't add folder [" + name + "] to " + parent.path + ": Already exists as file");
                    }
                }
            }

            PVRFile file = new PVRFile(parent, name, parent.getPath() + name);
            parent.addChild(file);

            notifyListeners(new TreeModelEvent(this, parent.getTreePath(), new int[]{parent.getChildCount() - 1}, new Object[]{file}));

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
                    log.debug("Folder : [{}] - [{}]", child.getPath(), child.getName());
                    dumpTree((PVRFolder) child);
                } else {
                    log.debug("File   : [{}] - [{}] - [{}]", child.getPath(), child.getName(), ((PVRFile) child).getDownloadURL());
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

    private void notifyListeners(TreeModelEvent e) {
        synchronized (treeModelListeners) {
            for (TreeModelListener l : treeModelListeners) {
                l.treeNodesInserted(e);
            }
        }
    }

    public void scrapeFTP() throws IOException {
        ftp.connect(getHostname());
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
                throw new IOException("Can't change FTP directory to " + directory);
            }

            for (FTPFile f : ftp.listFiles()) {
                if (f.getName().equals(".") || f.getName().equals("..")) {
                    // skip
                    continue;
                }

                if (f.isDirectory()) {
                    PVRFolder next = addFolder(directory, f.getName());
                    queue.add(next);
                } else if (f.isFile() && f.getName().endsWith(".ts")) {

                    PVRFile file = addFile(directory, f.getName());

                    file.setSize(f.getSize());

                    HMTFile hmt = getHMTForTs(file);

                    file.setDescription(hmt.getDesc());

                }
            }
        }
        ftp.disconnect();
    }

    private HMTFile getHMTForTs(PVRFile file) throws IOException {
        String target = file.getName().replaceAll("\\.ts$", ".hmt");
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
                log.debug("Found {}", DEVICE_NAME);
                setHostname(device.getIdentity().getDescriptorURL().getHost());
                populateTree(device);
            } else {
                log.info("Skiping device {} ", device.getDisplayString());
            }
        }
    };

    public void start() {
        if (running.compareAndSet(false, true)) {
            upnp.getControlPoint().search(new STAllHeader());
            new Thread(this, "BrowseQueue").start();
        }
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

    @Override
    public void run() {
        while (running.get()) {
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
                        running.set(false);
                        break;
                    }
                }
            } catch (InterruptedException ex) {
                running.set(false);
                return;
            }
        }
        dumpTree();
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
                    file.setDownloadURL(res.getValue());
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

        protected final String name;
        protected final String path;
        protected final PVRItem parent;
        protected final TreePath treePath;

        @SuppressWarnings("LeakingThisInConstructor")
        private PVRItem(PVRItem parent, String name, String path) {
            this.parent = parent;
            this.name = name;
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
        public int compareTo(PVRItem o) {
            if (isFolder() && o.isFile()) {
                // Folders go first
                return -1;
            } else if (isFile() && o.isFolder()) {
                return 1;
            } else {
                return getName().compareTo(o.getName());
            }
        }

        public abstract boolean isFile();

        public abstract boolean isFolder();

        public String getName() {
            return name;
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
            return name;
        }
    }

    public static class PVRFolder extends PVRItem {

        private final List<PVRItem> children;

        private PVRFolder(PVRItem parent, String path, String name) {
            super(parent, path, name);
            this.children = new ArrayList<>();
        }

        @Override
        public boolean isFolder() {
            return true;
        }

        @Override
        public boolean isFile() {
            return false;
        }

        public void addChild(PVRItem child) {
            children.add(child);
        }

        public PVRItem getChild(int index) {
            return children.get(index);
        }

        public int getChildCount() {
            return children.size();
        }
    }

    public static class PVRFile extends PVRItem {

        private long size = -1;
        private long downloaded = -1;
        private String downloadURL = null;
        private String description = "";

        @Override
        public boolean isFolder() {
            return false;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        private PVRFile(PVRItem parent, String path, String name) {
            super(parent, path, name);
        }

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

        public String getDownloadURL() {
            return downloadURL;
        }

        public void setDownloadURL(String downloadURL) {
            this.downloadURL = downloadURL;
        }

    }

}

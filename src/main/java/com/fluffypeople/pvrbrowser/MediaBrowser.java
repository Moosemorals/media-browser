/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.awt.BorderLayout;
import java.io.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
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
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric
 */
public class MediaBrowser extends javax.swing.JFrame {

    private final Logger log = LoggerFactory.getLogger(MediaBrowser.class);
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;

    private final UpnpService upnp;
    private File downloadFolder = null;
    private final DownloadThread dlManager;
    private final DefaultRegistryListener upnpListener = new DefaultRegistryListener() {

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            setStatus("Found device:" + device.getDisplayString());
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(device.getDisplayString());
            treeModel.insertNodeInto(node, rootNode, rootNode.getChildCount());
            populateTree(device, node);
        }
    };

    public MediaBrowser() {

        upnp = new UpnpServiceImpl(upnpListener);

        dlManager = new DownloadThread();
        dlManager.start();

        rootNode = new DefaultMutableTreeNode("Devices");
        treeModel = new DefaultTreeModel(rootNode);

        initComponents();
        setStatus("Looking for media servers");
        upnp.getControlPoint().search(new STAllHeader());
    }

    private void initComponents() {
        listScrollPane = new javax.swing.JScrollPane();
        downloadList = new DownloadProgressPanel();

        treeScrollPane = new javax.swing.JScrollPane();
        displayTree = new javax.swing.JTree();
        statusLabel = new javax.swing.JLabel();

        chooserButton = new javax.swing.JButton();
        downloadButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Media Browser");

        listScrollPane.setViewportView(downloadList);

        displayTree.setModel(treeModel);
        displayTree.setShowsRootHandles(true);
        treeScrollPane.setViewportView(displayTree);

        statusLabel.setFocusable(false);

        chooserButton.setText("Set Download Folder");
        chooserButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chooserButtonActionPerformed(evt);
            }
        });

        downloadButton.setText("Download Selected");
        downloadButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                downloadButtonActionPerformed(evt);
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.add(downloadButton, BorderLayout.LINE_END);
        buttonPanel.add(chooserButton, BorderLayout.LINE_START);

        java.awt.Container cp = getContentPane();
        cp.setLayout(new BoxLayout(cp, BoxLayout.PAGE_AXIS));
        cp.add(treeScrollPane);
        cp.add(listScrollPane);
        cp.add(buttonPanel);

        pack();
    }

    private boolean chooseDownloadFolder() {
        final JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            downloadFolder = fc.getSelectedFile();
            return true;
        } else {
            return false;
        }
    }

    private void chooserButtonActionPerformed(java.awt.event.ActionEvent evt) {
        chooseDownloadFolder();
    }

    private void downloadButtonActionPerformed(java.awt.event.ActionEvent evt) {
        while (downloadFolder == null) {
            chooseDownloadFolder();
        }

        for (TreePath p : displayTree.getSelectionPaths()) {
            TreeItemHolder item = (TreeItemHolder) ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
            if (item.getType() == TreeItemHolder.Type.ITEM) {
                String url = item.getPayload().getFirstResource().getValue();
                log.debug("Download URL " + url);
                dlManager.addTarget((Item) item.getPayload());

            }
        }
    }

    private void setStatus(final String status) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                statusLabel.setText(status);
            }
        });
    }

    private void populateTree(RemoteDevice device, DefaultMutableTreeNode parentNode) {
        Service service = device.findService(new UDAServiceType("ContentDirectory"));
        if (service != null) {
            upnp.getControlPoint().execute(new DeviceBrowse(service, "0", parentNode));
        }
    }

    private class DownloadThread implements Runnable {

        private final BlockingQueue<DownloadQueueItem> queue = new LinkedBlockingQueue<>();
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final HttpClient client = new DefaultHttpClient();

        public void start() {
            if (running.compareAndSet(false, true)) {
                Thread t = new Thread(this, "Download");
                t.start();
            }
        }

        @Override
        public void run() {
            while (running.get()) {
                DownloadQueueItem target;
                try {
                    target = queue.take();
                } catch (InterruptedException ex) {
                    log.error("Unexpected Iterruption", ex);
                    running.set(false);
                    return;
                }

                Item item = target.getTarget();
                log.debug("Downloading " + item.getTitle());
                target.setState(DownloadQueueItem.State.DOWNLOADING);
                try {
                    final HttpGet request = new HttpGet(item.getFirstResource().getValue());
                    final HttpResponse response = client.execute(request);

                    final StatusLine result = response.getStatusLine();
                    if (result.getStatusCode() != 200) {
                        log.error("Can't download item");
                        continue;
                    }

                    long len = Long.parseLong(response.getFirstHeader("Content-Length").getValue(), 10);
                    target.setSize(len);
                    final HttpEntity body = response.getEntity();

                    log.debug("Downloading " + len + " bytes");

                    final File downloadTarget = new File(downloadFolder, item.getTitle());
                    log.debug("Filename " + downloadTarget.getAbsolutePath());

                    try (InputStream in = body.getContent(); OutputStream out = new FileOutputStream(downloadTarget)) {
                        byte[] buffer = new byte[1024 * 4];
                        long count = 0;
                        int n = 0;
                        while (-1 != (n = in.read(buffer))) {
                            out.write(buffer, 0, n);
                            count += n;
                            target.setDownloaded(count);
                        }
                    }

                    target.setState(DownloadQueueItem.State.COMPLETED);
                } catch (IOException ex) {
                    log.error("IOExcption", ex);
                }
            }
        }

        public void addTarget(Item target) {
            DownloadQueueItem i = new DownloadQueueItem(target);
            downloadList.add(i);
            // allFiles.setMaximum(downloadList.getSize());
            queue.add(i);
        }
    }

    private class DeviceBrowse extends Browse {

        private final DefaultMutableTreeNode parent;
        private final Service service;

        public DeviceBrowse(Service service, String id, DefaultMutableTreeNode parent) {
            super(service, id, BrowseFlag.DIRECT_CHILDREN);
            this.parent = parent;
            this.service = service;
        }

        @Override
        public void received(ActionInvocation actionInvocation, DIDLContent didl) {
            List<Container> containers = didl.getContainers();
            Collections.sort(containers, new Comparator<Container>() {

                @Override
                public int compare(Container t, Container t1) {
                    return t.getTitle().compareTo(t1.getTitle());
                }

            });
            for (Container c : containers) {
                //       System.out.println("Container : " + c.getId() + c.getTitle());
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new TreeItemHolder(c, TreeItemHolder.Type.CONTAINER));
                treeModel.insertNodeInto(childNode, parent, parent.getChildCount());

                upnp.getControlPoint().execute(new DeviceBrowse(service, c.getId(), childNode));
            }
            List<Item> items = didl.getItems();
            Collections.sort(items, new Comparator<Item>() {

                @Override
                public int compare(Item t, Item t1) {
                    return t.getTitle().compareTo(t1.getTitle());
                }

            });
            for (Item i : items) {
                //           System.out.println("Item: " + i.getId() + i.getTitle());
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new TreeItemHolder(i, TreeItemHolder.Type.ITEM));
                treeModel.insertNodeInto(childNode, parent, parent.getChildCount());
            }
        }

        @Override
        public void updateStatus(Browse.Status status) {
            //     log.debug("Status: " + status.getDefaultMessage());
        }

        @Override
        public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
            //    log.debug("Failure: " + defaultMsg);
        }
    }
    private javax.swing.JButton chooserButton;

    private javax.swing.JTree displayTree;
    private javax.swing.JButton downloadButton;
    private DownloadProgressPanel downloadList;
    private javax.swing.JScrollPane listScrollPane;
    private javax.swing.JScrollPane treeScrollPane;
    private javax.swing.JLabel statusLabel;

}

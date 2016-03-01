/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
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
public class MediaBrowser extends JFrame {

    public static final String DOWNLOAD_DIRECTORY_KEY = "download_directory";

    private final Logger log = LoggerFactory.getLogger(MediaBrowser.class);
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final UpnpService upnp;
    private final Preferences prefs;
    private final DownloadManager dlManager;
    private final DefaultRegistryListener upnpListener = new DefaultRegistryListener() {

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            setStatus("Found device:" + device.getDisplayString());
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(device.getDisplayString());
            treeModel.insertNodeInto(node, rootNode, rootNode.getChildCount());
            populateTree(device, node);
        }
    };

    private JButton chooserButton;
    private JButton downloadButton;
    private JButton startButton;
    private JLabel statusLabel;
    private JTree displayTree;

    public MediaBrowser(Preferences prefs) {
        this.prefs = prefs;

        upnp = new UpnpServiceImpl(upnpListener);

        dlManager = new DownloadManager(prefs);

        rootNode = new DefaultMutableTreeNode("Devices");
        treeModel = new DefaultTreeModel(rootNode);

        initComponents();
        setStatus("Looking for media servers");
        upnp.getControlPoint().search(new STAllHeader());
    }

    private void initComponents() {

        JList downloadList = new JList(dlManager);

        displayTree = new JTree();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Media Browser");

        displayTree.setModel(treeModel);
        displayTree.setShowsRootHandles(true);

        statusLabel = new JLabel();
        statusLabel.setFocusable(false);

        chooserButton = new JButton();
        chooserButton.setText("Set Download Folder");
        chooserButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                chooserButtonActionPerformed(evt);
            }
        });

        downloadButton = new JButton();
        downloadButton.setText("Download Selected");
        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                downloadButtonActionPerformed(evt);
            }
        });

        startButton = new JButton();
        startButton.setText("Start Downloads");
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dlManager.start();
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.add(downloadButton);
        buttonPanel.add(chooserButton);
        buttonPanel.add(startButton);
        buttonPanel.add(statusLabel);

        java.awt.Container cp = getContentPane();
        cp.setLayout(new BorderLayout());
        cp.add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(displayTree), new JScrollPane(downloadList)), BorderLayout.CENTER);
        cp.add(buttonPanel, BorderLayout.SOUTH);

        pack();
    }

    private boolean chooseDownloadFolder() {

        File downloadDir = dlManager.getDownloadFolder();

        final JFileChooser fc = new JFileChooser(downloadDir);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            dlManager.setDownloadFolder(fc.getSelectedFile());

            return true;
        } else {
            return false;
        }
    }

    private void chooserButtonActionPerformed(ActionEvent evt) {
        chooseDownloadFolder();
    }

    private void downloadButtonActionPerformed(ActionEvent evt) {
        while (!dlManager.isDownloadFolderSet()) {
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

}

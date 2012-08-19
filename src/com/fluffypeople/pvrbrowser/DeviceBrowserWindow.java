/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import org.apache.log4j.Logger;
import org.teleal.cling.UpnpService;
import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.model.types.UDAServiceType;
import org.teleal.cling.registry.DefaultRegistryListener;
import org.teleal.cling.registry.Registry;
import org.teleal.cling.support.contentdirectory.callback.Browse;
import org.teleal.cling.support.model.BrowseFlag;
import org.teleal.cling.support.model.DIDLContent;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.Item;

/**
 *
 * @author Osric
 */
public class DeviceBrowserWindow extends DefaultRegistryListener implements TreeWillExpandListener, ActionListener {

    private static final Logger log = Logger.getLogger(DeviceBrowserWindow.class);
    private UpnpService upnp;
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final JButton downloadButton;
    private final JFrame frame;
    private static final String downloadString = "Download";
    private DownloadForm downloadForm = null;
    private final DefaultMutableTreeNode rootNode;

    public DeviceBrowserWindow() {

        rootNode = new DefaultMutableTreeNode("Devices");
        treeModel = new DefaultTreeModel(rootNode);

        frame = new JFrame("Media Browser");
        tree = new JTree(treeModel);
        downloadButton = new JButton(downloadString);
    }

    public void createAndDisplayGUI() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        tree.setShowsRootHandles(true);
        // tree.addTreeWillExpandListener(this);

        JScrollPane treeScrollPane = new JScrollPane(tree);

        downloadButton.addActionListener(this);
        downloadButton.setEnabled(true);

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(treeScrollPane, BorderLayout.CENTER);
        frame.getContentPane().add(downloadButton, BorderLayout.SOUTH);

        frame.pack();
        frame.setSize(400, 600);
        frame.setVisible(true);

    }

    public void setUpnpService(UpnpService upnpService) {
        this.upnp = upnpService;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String cmd = ae.getActionCommand();
        if (cmd == null) {
            return;
        } else if (cmd.equals(downloadString)) {

            while (DownloadForm.getDownloadFolder() == null) {
                final JFileChooser fc = new JFileChooser();
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

                int returnVal = fc.showOpenDialog(frame);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    DownloadForm.setDownloadFolder(fc.getSelectedFile());
                }
            }

            for (TreePath p : tree.getSelectionPaths()) {
                TreeItemHolder item = (TreeItemHolder) ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
                if (item.getType() == TreeItemHolder.Type.ITEM) {
                    String url = item.getPayload().getFirstResource().getValue();
                    log.debug("Download URL " + url);
                    if (downloadForm == null) {
                        downloadForm = new DownloadForm();
                        downloadForm.start();
                    }
                    downloadForm.addTarget((Item) item.getPayload());
                }
            }
        }
    }

    @Override
    public void treeWillExpand(TreeExpansionEvent tee) throws ExpandVetoException {
        TreePath tp = tee.getPath();
        Object[] path = tp.getPath();

        for (int i = 0; i < path.length; i++) {
            log.debug("Path: " + i + ": " + path[0].getClass().toString());
        }

        /*
        if (path.length == 1) {
            // Node hasn't expanded yet
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path[0];
            RemoteDevice device = (RemoteDevice) node.getUserObject();
            populateTree(device, node);
        }
        *
        */
    }

    private void populateTree(RemoteDevice device, DefaultMutableTreeNode parentNode) {
        Service service = device.findService(new UDAServiceType("ContentDirectory"));
        upnp.getControlPoint().execute(new DeviceBrowse(service, "0", parentNode));
    }

    @Override
    public void treeWillCollapse(TreeExpansionEvent tee) throws ExpandVetoException {
        // ignored
    }

    @Override
    public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(device.getDisplayString());
        treeModel.insertNodeInto(node, rootNode, rootNode.getChildCount());
        populateTree(device, node);
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
            for (Container c : didl.getContainers()) {
                //       System.out.println("Container : " + c.getId() + c.getTitle());
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new TreeItemHolder(c, TreeItemHolder.Type.CONTAINER));
                treeModel.insertNodeInto(childNode, parent, parent.getChildCount());

                upnp.getControlPoint().execute(new DeviceBrowse(service, c.getId(), childNode));
            }
            for (Item i : didl.getItems()) {
                //           System.out.println("Item: " + i.getId() + i.getTitle());
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new TreeItemHolder(i, TreeItemHolder.Type.ITEM));
                treeModel.insertNodeInto(childNode, parent, parent.getChildCount());
            }
        }

        @Override
        public void updateStatus(Status status) {
            //     log.debug("Status: " + status.getDefaultMessage());
        }

        @Override
        public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
            //    log.debug("Failure: " + defaultMsg);
        }
    }
}

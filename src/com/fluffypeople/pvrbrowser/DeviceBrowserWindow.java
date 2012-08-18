/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.apache.log4j.Logger;
import org.teleal.cling.UpnpService;
import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.types.UDAServiceType;
import org.teleal.cling.support.contentdirectory.callback.Browse;
import org.teleal.cling.support.model.BrowseFlag;
import org.teleal.cling.support.model.DIDLContent;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.Item;

/**
 *
 * @author Osric
 */
public class DeviceBrowserWindow implements TreeSelectionListener, ActionListener {

    private static final Logger log = Logger.getLogger(DeviceBrowserWindow.class);
    private final RemoteDevice target;
    private final UpnpService service;
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final JButton downloadButton;
    private final JFrame frame;
    private static final String downloadString = "Download";
    private DownloadForm downloadForm = null;

    public DeviceBrowserWindow(UpnpService service, RemoteDevice target) {
        this.target = target;
        this.service = service;

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(target.getDisplayString());
        treeModel = new DefaultTreeModel(rootNode);

        service.getControlPoint().execute(new DeviceBrowse("0", rootNode));

        frame = new JFrame(target.getDisplayString());
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        tree = new JTree(treeModel);
        tree.setShowsRootHandles(true);
        tree.addTreeSelectionListener(this);

        JScrollPane treeScrollPane = new JScrollPane(tree);

        downloadButton = new JButton(downloadString);
        downloadButton.addActionListener(this);
        downloadButton.setEnabled(true);

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(treeScrollPane, BorderLayout.CENTER);
        frame.getContentPane().add(downloadButton, BorderLayout.SOUTH);

        frame.pack();
        frame.setSize(400, 600);
        frame.setVisible(true);
    }

    @Override
    public void valueChanged(TreeSelectionEvent tse) {
        // do nothing
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

    private class DeviceBrowse extends Browse {

        private final DefaultMutableTreeNode parent;

        public DeviceBrowse(String id, DefaultMutableTreeNode parent) {
            super(target.findService(new UDAServiceType("ContentDirectory")), id, BrowseFlag.DIRECT_CHILDREN);
            this.parent = parent;
        }

        @Override
        public void received(ActionInvocation actionInvocation, DIDLContent didl) {
            for (Container c : didl.getContainers()) {
                //       System.out.println("Container : " + c.getId() + c.getTitle());

                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new TreeItemHolder(c, TreeItemHolder.Type.CONTAINER));
                treeModel.insertNodeInto(childNode, parent, parent.getChildCount());

                service.getControlPoint().execute(new DeviceBrowse(c.getId(), childNode));
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

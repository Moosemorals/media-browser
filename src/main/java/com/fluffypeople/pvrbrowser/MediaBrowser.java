/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
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
    private final DownloadManager dlManager;
    private final UpnpRemote upnpRemote;

    private JButton chooserButton;
    private JButton downloadButton;
    private JButton startButton;
    private JLabel statusLabel;
    private JTree displayTree;

    public MediaBrowser(Preferences prefs) {

        upnpRemote = new UpnpRemote(this);
        dlManager = new DownloadManager(prefs);
        initComponents();
        setStatus("Looking for media servers");

    }

    private void initComponents() {

        JList downloadList = new JList(dlManager);

        displayTree = new JTree();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Media Browser");

        displayTree.setModel(upnpRemote.getTreeModel());
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
            RemoteItem item = (RemoteItem) ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
            if (item.getType() == RemoteItem.Type.ITEM) {
                String url = item.getPayload().getFirstResource().getValue();
                log.debug("Download URL " + url);
                dlManager.addTarget((Item) item.getPayload());

            }
        }
    }

    public void setStatus(final String status) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                statusLabel.setText(status);
            }
        });
    }

}

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
public class UI extends JFrame {

    public static final String DOWNLOAD_DIRECTORY_KEY = "download_directory";

    private final Logger log = LoggerFactory.getLogger(UI.class);
    private final DownloadManager dlManager;
    private final UpnpRemote upnpRemote;
    private final PVR pvr;

    private JButton chooserButton;
    private JButton downloadButton;
    private JButton startButton;
    private JLabel statusLabel;
    private JTree displayTree;

    public UI(Preferences prefs) {

        pvr = new PVR();
        upnpRemote = new UpnpRemote(pvr);

        dlManager = new DownloadManager(prefs);
        initComponents();
        upnpRemote.start();
    }

    private void initComponents() {

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Media Browser");

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
        downloadButton.setText("Queue Selected");
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

        JList downloadList = new JList(dlManager);

        displayTree = new JTree();
        displayTree.setModel(pvr);
        displayTree.setRootVisible(false);
        displayTree.setShowsRootHandles(true);

        java.awt.Container cp = getContentPane();
        cp.setLayout(new BorderLayout());
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(displayTree), new JScrollPane(downloadList));
        splitPane.setResizeWeight(0.5);
        cp.add(splitPane, BorderLayout.CENTER);
        cp.add(buttonPanel, BorderLayout.SOUTH);

        pack();
    }

    void expandTreeRoot(DefaultMutableTreeNode root) {
        log.debug("Expanding {}", root);
        displayTree.expandPath(new TreePath(root.getPath()));
    }

    private boolean chooseDownloadFolder() {

        File downloadDir = dlManager.getDownloadPath();

        final JFileChooser fc = new JFileChooser(downloadDir);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            dlManager.setDownloadPath(fc.getSelectedFile());

            return true;
        } else {
            return false;
        }
    }

    private void chooserButtonActionPerformed(ActionEvent evt) {
        chooseDownloadFolder();
    }

    private void downloadButtonActionPerformed(ActionEvent evt) {
        while (!dlManager.isDownloadPathSet()) {
            chooseDownloadFolder();
        }

        for (TreePath p : displayTree.getSelectionPaths()) {
            RemoteItem item = (RemoteItem) ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
            if (item.getType() == RemoteItem.Type.File) {
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

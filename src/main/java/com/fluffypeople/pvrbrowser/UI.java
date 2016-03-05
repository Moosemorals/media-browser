/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import com.fluffypeople.pvrbrowser.PVR.PVRFile;
import com.fluffypeople.pvrbrowser.PVR.PVRItem;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
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
    private final PVR pvr;

    private JButton chooserButton;
    private JButton downloadButton;
    private JButton startButton;
    private JLabel statusLabel;
    private JTree displayTree;

    private final Action startStopAction = new AbstractAction("Start downloading") {

        private boolean downloading = false;

        @Override
        public void actionPerformed(ActionEvent e) {

            if (!downloading) {
                log.debug("Starting downloads");
                dlManager.start();
                putValue(NAME, "Stop downloading");
                downloading = true;
            } else {
                log.debug("Stopping downloads");
                dlManager.stop();
                putValue(NAME, "Start downloading");
                downloading = false;
            }
        }
    };

    private final Action queueAction = new AbstractAction("Queue selected") {
        @Override
        public void actionPerformed(ActionEvent e) {
            while (!dlManager.isDownloadPathSet()) {
                chooseDownloadFolder();
            }

            for (TreePath p : displayTree.getSelectionPaths()) {
                PVRItem item = (PVRItem) p.getLastPathComponent();

                if (item.isFile()) {
                    log.debug("Queuing {}", item);
                    dlManager.addTarget((PVRFile) item);
                }
            }
        }
    };

    private final Action chooseAction = new AbstractAction("Choose download folder") {
        @Override
        public void actionPerformed(ActionEvent e) {
            chooseDownloadFolder();
        }

    };

    public UI(Preferences prefs) {

        pvr = new PVR();

        dlManager = new DownloadManager(prefs);
        initComponents();
        pvr.start();
    }

    private void initComponents() {

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Media Browser");

        statusLabel = new JLabel();
        statusLabel.setFocusable(false);

        chooserButton = new JButton(chooseAction);
        downloadButton = new JButton(queueAction);
        startButton = new JButton(startStopAction);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.add(downloadButton);
        buttonPanel.add(chooserButton);
        buttonPanel.add(startButton);
        buttonPanel.add(statusLabel);

        JList downloadList = new JList(dlManager);

        displayTree = new JTree();
        displayTree.setModel(pvr);
        displayTree.setCellRenderer(new PVRFileTreeCellRenderer());
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

    public void setStatus(final String status) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                statusLabel.setText(status);
            }
        });
    }

}

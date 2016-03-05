/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import com.fluffypeople.pvrbrowser.PVR.PVRFile;
import com.fluffypeople.pvrbrowser.PVR.PVRItem;
import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric
 */
public class UI extends JFrame {

    public static final String KEY_DOWNLOAD_DIRECTORY = "download_directory";
    public static final String KEY_DIVIDER_LOCATION = "divider_location";
    public static final String KEY_FRAME_TOP = "frame_top";
    public static final String KEY_FRAME_LEFT = "frame_left";
    public static final String KEY_FRAME_WIDTH = "frame_width";
    public static final String KEY_FRAME_HEIGHT = "frame_height";
    public static final String KEY_FRAME_KNOWN = "frame_bounds";

    private final Logger log = LoggerFactory.getLogger(UI.class);
    private final DownloadManager dlManager;
    private final PVR pvr;
    private final Preferences prefs;
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
                if (item.isFile() && !((PVRFile) item).isHighDef()) {

                    if (dlManager.queueFile((PVRFile) item)) {
                        startStopAction.setEnabled(true);
                    }
                }
            }
        }
    };

    private final Action removeLockAction = new AbstractAction("Remove lock") {
        @Override
        public void actionPerformed(ActionEvent e) {
            for (TreePath p : displayTree.getSelectionPaths()) {
                PVRItem item = (PVRItem) p.getLastPathComponent();
                if (item.isFile() && ((PVRFile) item).isLocked()) {
                    PVRFile file = (PVRFile) item;
                    try {
                        pvr.unlockFile(file);
                    } catch (IOException ex) {
                        log.error("Problem unlocking " + file.path + "/" + file.filename + ": " + ex.getMessage(), ex);
                        file.setState(PVRFile.State.Error);
                    }
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

    private final Action quitAction = new AbstractAction("Exit") {
        @Override
        public void actionPerformed(ActionEvent e) {
            quit();
        }
    };

    public UI(Preferences prefs) {
        pvr = new PVR();
        this.prefs = prefs;
        dlManager = new DownloadManager(prefs);
        initComponents();
        pvr.start();
    }

    private void initComponents() {

        queueAction.setEnabled(false);
        startStopAction.setEnabled(false);
        removeLockAction.setEnabled(false);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setTitle("Media Browser");

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                saveBounds(e.getComponent().getBounds());
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                saveBounds(e.getComponent().getBounds());
            }

            private void saveBounds(Rectangle bounds) {
                prefs.putInt(KEY_FRAME_TOP, bounds.y);
                prefs.putInt(KEY_FRAME_LEFT, bounds.x);
                prefs.putInt(KEY_FRAME_WIDTH, bounds.width);
                prefs.putInt(KEY_FRAME_HEIGHT, bounds.height);
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                log.debug("Closing window");

                UI.this.setVisible(false);
                UI.this.dispose();
                quit();
            }
        });

        setBounds(new Rectangle(
                prefs.getInt(KEY_FRAME_LEFT, 0),
                prefs.getInt(KEY_FRAME_TOP, 0),
                prefs.getInt(KEY_FRAME_WIDTH, 640),
                prefs.getInt(KEY_FRAME_HEIGHT, 480)
        ));

        JMenuBar menuBar = new JMenuBar();

        JMenu menu = new JMenu("File");

        menu.add(chooseAction);
        menu.add(startStopAction);
        menu.add(quitAction);

        menuBar.add(menu);

        setJMenuBar(menuBar);

        final JPopupMenu itemPopup = new JPopupMenu();
        itemPopup.add(queueAction);
        itemPopup.add(removeLockAction);

        final JLabel downloadLabel = new JLabel(dlManager.getDownloadPath().getPath());

        prefs.addPreferenceChangeListener(new PreferenceChangeListener() {
            @Override
            public void preferenceChange(PreferenceChangeEvent evt) {
                if (evt.getKey().equals(KEY_DOWNLOAD_DIRECTORY)) {
                    downloadLabel.setText(evt.getNewValue());
                }
            }
        });

        statusLabel = new JLabel();
        statusLabel.setFocusable(false);

        startButton = new JButton(startStopAction);

        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.LINE_AXIS));
        statusPanel.add(startButton);
        statusPanel.add(downloadLabel);
        statusPanel.add(statusLabel);

        JList downloadList = new JList(dlManager);

        displayTree = new JTree();
        displayTree.setModel(pvr);
        displayTree.setCellRenderer(new PVRFileTreeCellRenderer(itemPopup));
        displayTree.setRootVisible(false);
        displayTree.setShowsRootHandles(true);

        displayTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int row = displayTree.getRowForLocation(e.getX(), e.getY());
                if (row == -1) {
                    displayTree.clearSelection();
                }
            }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    itemPopup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        displayTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                queueAction.setEnabled(false);
                removeLockAction.setEnabled(false);
                TreePath[] selectionPaths = displayTree.getSelectionPaths();
                if (selectionPaths == null) {
                    return;
                }
                for (TreePath p : selectionPaths) {
                    if (((PVRItem) p.getLastPathComponent()).isFile()) {
                        PVRFile file = (PVRFile) p.getLastPathComponent();
                        if (!file.isHighDef()) {
                            queueAction.setEnabled(true);
                        }
                        if (file.isLocked()) {
                            removeLockAction.setEnabled(true);
                        }
                    }
                }

            }
        });

        java.awt.Container cp = getContentPane();
        cp.setLayout(new BorderLayout());
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(displayTree), new JScrollPane(downloadList));

        splitPane.setDividerLocation(prefs.getInt(KEY_DIVIDER_LOCATION, -1));

        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                prefs.putInt(KEY_DIVIDER_LOCATION, (Integer) evt.getNewValue());

            }
        });

        cp.add(splitPane, BorderLayout.CENTER);
        cp.add(statusPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    private int[] removeRow(int[] selectedRows, int row) {
        int i, j;
        for (i = j = 0; j < selectedRows.length; ++j) {
            if (j == row) {
                selectedRows[i++] = selectedRows[j];
            }
        }
        return Arrays.copyOf(selectedRows, i);
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

    private void quit() {
        pvr.stop();
        dlManager.stop();
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

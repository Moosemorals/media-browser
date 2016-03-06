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
package com.moosemorals.mediabrowser;

import com.moosemorals.mediabrowser.DownloadManager.DownloadStatusListener;
import com.moosemorals.mediabrowser.PVR.PVRFile;
import com.moosemorals.mediabrowser.PVR.PVRItem;
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
import java.util.List;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The 'View' part of a MVC pattern, if people actually thought like that.,
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
public class UI extends JFrame implements DownloadStatusListener {

    public static final String KEY_DOWNLOAD_DIRECTORY = "download_directory";
    public static final String KEY_DIVIDER_LOCATION = "divider_location";
    public static final String KEY_FRAME_TOP = "frame_top";
    public static final String KEY_FRAME_LEFT = "frame_left";
    public static final String KEY_FRAME_WIDTH = "frame_width";
    public static final String KEY_FRAME_HEIGHT = "frame_height";
    public static final String KEY_FRAME_KNOWN = "frame_bounds";

    private final Logger log = LoggerFactory.getLogger(UI.class);
    private final DownloadManager downloader;
    private final PVR pvr;
    private final Preferences preferences;
    private final JButton startButton;
    private final JLabel statusLabel;
    private final JTree displayTree;
    private final JList<PVRFile> downloadList;
    private boolean downloading = false;

    private final Action startStopAction = new AbstractAction("Start downloading") {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (downloading) {
                log.debug("Stopping downloads");
                downloader.stop();
                putValue(NAME, "Start downloading");
            } else {
                log.debug("Starting downloads");
                downloader.start();
                putValue(NAME, "Stop downloading");
            }
        }
    };

    private final Action queueAction = new AbstractAction("Queue selected") {
        @Override
        public void actionPerformed(ActionEvent e) {
            while (!downloader.isDownloadPathSet()) {
                downloader.setDownloadPath(chooseDownloadFolder(downloader.getDownloadPath()));
            }

            for (TreePath p : displayTree.getSelectionPaths()) {
                PVRItem item = (PVRItem) p.getLastPathComponent();
                if (item.isFile() && !((PVRFile) item).isHighDef()) {

                    if (downloader.add((PVRFile) item)) {
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

    private final Action chooseDefaultDownloadPathAction = new AbstractAction("Set default download folder") {
        @Override
        public void actionPerformed(ActionEvent e) {
            downloader.setDownloadPath(chooseDownloadFolder(downloader.getDownloadPath()));
        }
    };

    private final Action chooseDownloadPathAction = new AbstractAction("Set download folder") {
        @Override
        public void actionPerformed(ActionEvent e) {

            List<PVRFile> selected = downloadList.getSelectedValuesList();
            if (!selected.isEmpty()) {
                File downloadPath = chooseDownloadFolder(selected.get(0).getDownloadPath());
                downloader.changeDownloadPath(selected, downloadPath);
            }
        }
    };

    private final Action removeSelectedAction = new AbstractAction("Remove from queue") {
        @Override
        public void actionPerformed(ActionEvent e) {
            downloader.remove(downloadList.getSelectedValuesList());
        }
    };

    private final Action quitAction = new AbstractAction("Exit") {
        @Override
        public void actionPerformed(ActionEvent e) {
            quit();
        }
    };

    public UI(Preferences prefs) {
        this.preferences = prefs;

        pvr = new PVR();
        downloader = new DownloadManager(preferences);

        queueAction.setEnabled(false);
        startStopAction.setEnabled(false);
        removeLockAction.setEnabled(false);
        removeSelectedAction.setEnabled(false);
        chooseDownloadPathAction.setEnabled(false);

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
                preferences.putInt(KEY_FRAME_TOP, bounds.y);
                preferences.putInt(KEY_FRAME_LEFT, bounds.x);
                preferences.putInt(KEY_FRAME_WIDTH, bounds.width);
                preferences.putInt(KEY_FRAME_HEIGHT, bounds.height);
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

        menu.add(chooseDefaultDownloadPathAction);
        menu.add(startStopAction);
        menu.add(quitAction);

        menuBar.add(menu);

        setJMenuBar(menuBar);

        final JPopupMenu treePopup = new JPopupMenu();
        treePopup.add(queueAction);
        treePopup.add(removeLockAction);

        final JPopupMenu listPopup = new JPopupMenu();
        listPopup.add(chooseDownloadPathAction);
        listPopup.add(removeSelectedAction);
        listPopup.add(startStopAction);

        final JLabel downloadLabel = new JLabel(downloader.getDownloadPath().getPath());

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

        downloadList = new JList(downloader);
        downloadList.setCellRenderer(new PVRFileListCellRenderer());
        downloadList.setDragEnabled(true);
        downloadList.setDropMode(DropMode.INSERT);
        downloadList.setTransferHandler(new PVRFileTransferHandler());

        downloadList.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    listPopup.show(e.getComponent(), e.getX(), e.getY());
                }
            }

        });

        downloadList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                removeSelectedAction.setEnabled(false);
                chooseDownloadPathAction.setEnabled(false);
                if (downloadList.getSelectedIndices().length > 0) {
                    removeSelectedAction.setEnabled(true);
                    if (!downloading) {
                        chooseDownloadPathAction.setEnabled(true);
                    }
                }
            }
        });

        displayTree = new JTree();
        displayTree.setModel(pvr);
        displayTree.setCellRenderer(new PVRFileTreeCellRenderer(treePopup));
        displayTree.setRootVisible(false);
        displayTree.setShowsRootHandles(true);
        displayTree.setDragEnabled(true);
        displayTree.setTransferHandler(new PVRFileTransferHandler());
        displayTree.setDropTarget(null);

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
                    treePopup.show(e.getComponent(), e.getX(), e.getY());
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
                preferences.putInt(KEY_DIVIDER_LOCATION, (Integer) evt.getNewValue());
            }
        });

        cp.add(splitPane, BorderLayout.CENTER);
        cp.add(statusPanel, BorderLayout.SOUTH);

        setVisible(true);

    }

    public void start() {
        downloadProgress(0, 0, 0, 0, -1);
        downloader.setDownloadStatusListener(this);
        pvr.start();
    }

    private File chooseDownloadFolder(File initial) {
        final JFileChooser fc = new JFileChooser(initial);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Choose download folder");

        int returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            return fc.getSelectedFile();
        } else {
            return null;
        }
    }

    private void quit() {
        pvr.stop();
        downloader.stop();
    }

    public void setStatus(final String status) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                statusLabel.setText(status);
            }
        });
    }

    @Override
    public void downloadStatusChanged(boolean running) {
        chooseDownloadPathAction.setEnabled(!running);
        this.downloading = running;
    }

    @Override
    public void downloadProgress(long totalQueued, long totalDownloaded, long currentFile, long currentDownloaded, double rate) {
        if (rate < 0.0) {
            statusLabel.setText(String.format("Total queued %s (Downloaded %s %.0f%%)",
                    PVR.humanReadableSize(totalQueued),
                    PVR.humanReadableSize(totalDownloaded),
                    totalQueued > 0
                            ? ((double) totalDownloaded / (double) totalQueued) * 100.0
                            : 0
            ));
        } else {
            statusLabel.setText(String.format("Total queued %s (Downloaded %s %.0f%%) - Current %s (Downloaded %s %.0f%%) - Rate %s/s",
                    PVR.humanReadableSize(totalQueued),
                    PVR.humanReadableSize(totalDownloaded),
                    ((double) totalDownloaded / (double) totalQueued) * 100.0,
                    PVR.humanReadableSize(currentFile),
                    PVR.humanReadableSize(currentDownloaded),
                    currentFile > 0
                            ? ((double) currentDownloaded / (double) currentFile) * 100.0
                            : 0,
                    PVR.humanReadableSize((long) rate)
            ));
        }
    }

}

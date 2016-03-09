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
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URL;
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
class UI implements DownloadStatusListener, PVR.ConnectionListener {

    static final String ACTION_START_STOP = "start";
    static final String ACTION_QUEUE = "queue";
    static final String ACTION_LOCK = "lock";
    static final String ACTION_CHOOSE_DEFAULT = "choose_default";
    static final String ACTION_CHOOSE = "choose";
    static final String ACTION_REMOVE = "remove";
    static final String ACTION_QUIT = "quit";
    static final String ACTION_TRAY = "tray";

    private final Logger log = LoggerFactory.getLogger(UI.class);
    private final DownloadManager downloader;
    private final PVR pvr;
    private final Preferences prefs;
    private final JButton startButton;
    private final JLabel statusLabel;
    private final JTree displayTree;
    private final JSplitPane splitPane;
    private final JList<PVRFile> downloadList;
    private final JFrame window;
    private final TrayIcon trayIcon;
    private final Main main;
    private final RateTracker rateTracker;

    private final Action startStopAction, queueAction, removeLockAction, chooseDefaultDownloadPathAction,
            chooseDownloadPathAction, removeSelectedAction, quitAction, setMinimiseToTrayAction,
            setAutoDownloadAction, setSaveDownloadListAction, setShowMessageOnCompleteAction;

    UI(Main m) {

        this.main = m;
        prefs = main.getPreferences();

        this.setShowMessageOnCompleteAction = new PreferenceAction(prefs, "Show completed notification", Main.KEY_MESSAGE_ON_COMPLETE);
        this.setSaveDownloadListAction = new PreferenceAction(prefs, "Save download list", Main.KEY_SAVE_DOWNLOAD_LIST);
        this.setAutoDownloadAction = new PreferenceAction(prefs, "Automaticaly download next", Main.KEY_AUTO_DOWNLOAD);
        this.setMinimiseToTrayAction = new PreferenceAction(prefs, "Automaticaly download next", Main.KEY_MINIMISE_TO_TRAY);

        this.chooseDefaultDownloadPathAction = new LocalAction(main, "Set default download folder", ACTION_CHOOSE_DEFAULT);
        this.chooseDownloadPathAction = new LocalAction(main, "Set download folder", ACTION_CHOOSE);
        this.quitAction = new LocalAction(main, "Exit", ACTION_QUIT);
        this.queueAction = new LocalAction(main, "Queue selected", ACTION_QUEUE);
        this.removeLockAction = new LocalAction(main, "Remove lock", ACTION_LOCK);
        this.removeSelectedAction = new LocalAction(main, "Remove from queue", ACTION_REMOVE);

        this.startStopAction = new LocalAction(main, "Start downloading", ACTION_START_STOP);

        pvr = main.getPVR();
        downloader = main.getDownloadManager();

        rateTracker = new RateTracker(10);

        queueAction.setEnabled(false);
        startStopAction.setEnabled(false);
        removeLockAction.setEnabled(false);
        removeSelectedAction.setEnabled(false);
        chooseDownloadPathAction.setEnabled(false);

        Image applicationIcon = loadIcon("/application_icon.png");

        window = new JFrame("Media Browser");

        window.setIconImage(applicationIcon);

        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        window.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                saveBounds(e.getComponent().getBounds());
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                saveBounds(e.getComponent().getBounds());
            }

            private void saveBounds(Rectangle bounds) {
                prefs.putInt(Main.KEY_FRAME_TOP, bounds.y);
                prefs.putInt(Main.KEY_FRAME_LEFT, bounds.x);
                prefs.putInt(Main.KEY_FRAME_WIDTH, bounds.width);
                prefs.putInt(Main.KEY_FRAME_HEIGHT, bounds.height);
            }
        });

        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                main.stop();
            }

            @Override
            public void windowIconified(WindowEvent e) {
                if (prefs.getBoolean(Main.KEY_MINIMISE_TO_TRAY, true)) {
                    window.dispose();
                }
            }

        });

        JMenuBar menuBar = new JMenuBar();

        JMenu menu;
        menu = new JMenu("File");

        menu.add(startStopAction);
        menu.add(quitAction);

        menuBar.add(menu);

        menu = new JMenu("Options");

        menu.add(chooseDefaultDownloadPathAction);

        JCheckBoxMenuItem jCheckBoxMenuItem;
        jCheckBoxMenuItem = new JCheckBoxMenuItem(setMinimiseToTrayAction);
        if (SystemTray.isSupported()) {
            jCheckBoxMenuItem.setState(prefs.getBoolean(Main.KEY_MINIMISE_TO_TRAY, true));
        } else {
            setMinimiseToTrayAction.setEnabled(false);
            prefs.putBoolean(Main.KEY_MINIMISE_TO_TRAY, false);
            jCheckBoxMenuItem.setState(false);
        }
        menu.add(jCheckBoxMenuItem);

        jCheckBoxMenuItem = new JCheckBoxMenuItem(setAutoDownloadAction);
        jCheckBoxMenuItem.setState(prefs.getBoolean(Main.KEY_AUTO_DOWNLOAD, true));
        menu.add(jCheckBoxMenuItem);

        jCheckBoxMenuItem = new JCheckBoxMenuItem(setShowMessageOnCompleteAction);
        jCheckBoxMenuItem.setState(prefs.getBoolean(Main.KEY_MESSAGE_ON_COMPLETE, true));
        menu.add(jCheckBoxMenuItem);

        setSaveDownloadListAction.setEnabled(false); // not implemented yet
        jCheckBoxMenuItem = new JCheckBoxMenuItem(setSaveDownloadListAction);
        jCheckBoxMenuItem.setState(prefs.getBoolean(Main.KEY_SAVE_DOWNLOAD_LIST, false));
        menu.add(jCheckBoxMenuItem);

        menuBar.add(menu);

        window.setJMenuBar(menuBar);

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
                if (evt.getKey().equals(Main.KEY_DOWNLOAD_DIRECTORY)) {
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
        statusPanel.add(Box.createHorizontalStrut(8));
        statusPanel.add(downloadLabel);
        statusPanel.add(Box.createHorizontalStrut(8));
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
                    if (!main.isDownloading()) {
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

        window.setLayout(new BorderLayout());

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(displayTree), new JScrollPane(downloadList));

        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                prefs.putInt(Main.KEY_DIVIDER_LOCATION, (Integer) evt.getNewValue());
            }
        });

        window.add(splitPane, BorderLayout.CENTER);
        window.add(statusPanel, BorderLayout.SOUTH);

        if (SystemTray.isSupported()) {
            PopupMenu trayPopup = new PopupMenu();

            MenuItem item;
            item = new MenuItem(startStopAction.getValue(AbstractAction.NAME).toString());
            item.addActionListener(startStopAction);
            trayPopup.add(item);

            item = new MenuItem(quitAction.getValue(AbstractAction.NAME).toString());
            item.addActionListener(quitAction);
            trayPopup.add(item);

            trayIcon = new TrayIcon(applicationIcon, "Media Browser", trayPopup);
            trayIcon.setImageAutoSize(true);
            trayIcon.setActionCommand(ACTION_TRAY);
            trayIcon.addActionListener(main);
            try {
                SystemTray.getSystemTray().add(trayIcon);
            } catch (AWTException ex) {
                log.error("Can't add system tray icon: {}", ex.getMessage(), ex);
            }
        } else {
            trayIcon = null;
        }

    }

    void showWindow() {
        downloadProgress(0, 0, 0, 0, -1);
        downloader.setDownloadStatusListener(this);
        window.pack();

        window.setBounds(new Rectangle(
                prefs.getInt(Main.KEY_FRAME_LEFT, 0),
                prefs.getInt(Main.KEY_FRAME_TOP, 0),
                prefs.getInt(Main.KEY_FRAME_WIDTH, 640),
                prefs.getInt(Main.KEY_FRAME_HEIGHT, 480)
        ));

        splitPane.setDividerLocation(prefs.getInt(Main.KEY_DIVIDER_LOCATION, window.getWidth() / 2));
        window.setState(JFrame.NORMAL);
        window.setVisible(true);
    }

    private File chooseDownloadFolder(File initial) {
        final JFileChooser fc = new JFileChooser(initial);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Choose download folder");

        int returnVal = fc.showOpenDialog(window);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            return fc.getSelectedFile();
        } else {
            return null;
        }
    }

    void stop() {
        window.setVisible(false);
        window.dispose();
        if (SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    void setStatus(final String status) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                statusLabel.setText(status);
            }
        });
    }

    @Override
    public void downloadStatusChanged(boolean running) {
        rateTracker.reset();
        setStartButtonStatus(downloader.downloadsAvailible(), downloader.isDownloading());
    }

    @Override
    public void downloadProgress(long totalQueued, long totalDownloaded, long currentFile, long currentDownloaded, double rate) {

        String message;

        message = String.format("Total %s of %s (%.0f%%)",
                PVR.humanReadableSize(totalDownloaded),
                PVR.humanReadableSize(totalQueued),
                totalQueued > 0
                        ? (totalDownloaded / (double) totalQueued) * 100.0
                        : 0
        );

        if (rate >= 0) {
            rateTracker.addRate(rate);
            message += String.format(" - Current %s of %s (%.0f%%) - Rate %s/s",
                    PVR.humanReadableSize(currentDownloaded),
                    PVR.humanReadableSize(currentFile),
                    currentFile > 0
                            ? (currentDownloaded / (double) currentFile) * 100.0
                            : 0,
                    PVR.humanReadableSize((long) rateTracker.getRate())
            );
        }

        statusLabel.setText(message);
        trayIcon.setToolTip(message);
    }

    private Image loadIcon(String path) {
        URL imageURL = UI.class.getResource(path);

        if (imageURL != null) {
            return (new ImageIcon(imageURL)).getImage();
        } else {
            log.error("Can't find resource for {}", path);
            return null;
        }
    }

    @Override
    public void downloadCompleted(PVRFile target) {
        if (prefs.getBoolean(Main.KEY_MESSAGE_ON_COMPLETE, true)) {
            trayIcon.displayMessage("Download Completed", String.format("%s has downloaded to %s/%s.ts", target.getTitle(), target.getDownloadPath(), target.getDownloadFilename()), TrayIcon.MessageType.INFO);
        }
    }

    @Override
    public void onConnect() {

    }

    @Override
    public void onDisconnect() {
        startStopAction.setEnabled(false);
    }

    File showFileChooser(File base) {
        return chooseDownloadFolder(base);
    }

    TreePath[] getTreeSelected() {
        return displayTree.getSelectionPaths();
    }

    List<PVRFile> getListSelected() {
        return downloadList.getSelectedValuesList();
    }

    void setStartButtonStatus(boolean queued, boolean downloading) {
        startStopAction.setEnabled(queued);
        if (downloading) {
            startStopAction.putValue(Action.NAME, "Stop downloading");
        } else {
            startStopAction.putValue(Action.NAME, "Start donwloading");
        }
    }

    boolean isVisible() {
        return window.isVisible();
    }

}

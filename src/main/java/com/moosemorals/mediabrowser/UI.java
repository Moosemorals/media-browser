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

import com.moosemorals.mediabrowser.PVR.PVRFile;
import com.moosemorals.mediabrowser.PVR.PVRItem;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
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
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
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
class UI {

    static final String ACTION_START_STOP = "start";
    static final String ACTION_QUEUE = "queue";
    static final String ACTION_LOCK = "lock";
    static final String ACTION_CHOOSE_DEFAULT = "choose_default";
    static final String ACTION_CHOOSE = "choose";
    static final String ACTION_REMOVE = "remove";
    static final String ACTION_QUIT = "quit";
    static final String ACTION_TRAY = "tray";
    static final String ACTION_RESTORE = "restore";

    static final String ICON_CONNECTED = "Blue";
    static final String ICON_DISCONNECTED = "Grey";
    static final String ICON_DOWNLOADING = "Red";
    static final String ICON_ERROR = "Error";

    private static final String[] ICON_COLORS = {ICON_DISCONNECTED, ICON_CONNECTED, ICON_DOWNLOADING, ICON_ERROR};
    private static final int[] ICON_SIZES = {32, 24, 20, 16};
    private static final int INFOBOX_PADDING = 6;

    private final DownloadManager downloader;
    private final JButton startButton;
    private final JFrame window;
    private final JLabel statusLabel;
    private final JList<PVRFile> downloadList;
    private final JSplitPane horizontalSplitPane;
    private final JSplitPane verticalSplitPane;
    private final JTextArea infoBox;
    private final JTree displayTree;
    private final Logger log = LoggerFactory.getLogger(UI.class);
    private final Map<String, List<Image>> icons;
    private final Main main;
    private final Preferences prefs;
    private final PVR pvr;
    private final TrayIcon trayIcon;

    private final Action actionStartStop, actionQueue, actionRemoveLock, actionChooseDefaultDownloadPath,
            actionChooseDownloadPath, actionRemoveSelected, actionQuit, actionRestore, actionSetMinimiseToTray,
            actionSetAutoDownload, actionSetSaveDownloadList, actionSetShowMessageOnComplete;

    UI(Main m) {

        this.main = m;
        prefs = main.getPreferences();

        actionChooseDefaultDownloadPath = new LocalAction(main, "Set default download folder", ACTION_CHOOSE_DEFAULT);
        actionChooseDownloadPath = new LocalAction(main, "Set download folder", ACTION_CHOOSE);
        actionQuit = new LocalAction(main, "Exit", ACTION_QUIT);
        actionQueue = new LocalAction(main, "Queue selected", ACTION_QUEUE);
        actionRemoveLock = new LocalAction(main, "Remove lock", ACTION_LOCK);
        actionRemoveSelected = new LocalAction(main, "Remove from queue", ACTION_REMOVE);
        actionRestore = new LocalAction(main, "Restore window", ACTION_RESTORE);
        actionStartStop = new LocalAction(main, "Start downloading", ACTION_START_STOP);
        actionSetAutoDownload = new PreferenceAction(prefs, "Automaticaly download next", Main.KEY_AUTO_DOWNLOAD);
        actionSetMinimiseToTray = new PreferenceAction(prefs, "Minimise to tray", Main.KEY_MINIMISE_TO_TRAY);
        actionSetShowMessageOnComplete = new PreferenceAction(prefs, "Show completed notification", Main.KEY_MESSAGE_ON_COMPLETE);
        actionSetSaveDownloadList = new PreferenceAction(prefs, "Save download queue", Main.KEY_SAVE_DOWNLOAD_LIST);

        pvr = main.getPVR();
        downloader = main.getDownloadManager();

        actionQueue.setEnabled(false);
        actionRemoveLock.setEnabled(false);
        actionRemoveSelected.setEnabled(false);
        actionChooseDownloadPath.setEnabled(false);

        icons = new HashMap<>();
        for (String color : ICON_COLORS) {
            icons.put(color, loadIcons(color));
        }

        window = new JFrame("Media Browser");

        window.setIconImages(icons.get(ICON_CONNECTED));

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

        menu.add(actionStartStop);
        menu.addSeparator();
        menu.add(actionChooseDefaultDownloadPath);
        menu.addSeparator();
        menu.add(actionQuit);

        menuBar.add(menu);

        menu = new JMenu("Options");

        JCheckBoxMenuItem jCheckBoxMenuItem;
        jCheckBoxMenuItem = new JCheckBoxMenuItem(actionSetMinimiseToTray);
        if (SystemTray.isSupported()) {
            jCheckBoxMenuItem.setState(prefs.getBoolean(Main.KEY_MINIMISE_TO_TRAY, true));
        } else {
            actionSetMinimiseToTray.setEnabled(false);
            prefs.putBoolean(Main.KEY_MINIMISE_TO_TRAY, false);
            jCheckBoxMenuItem.setState(false);
        }
        menu.add(jCheckBoxMenuItem);

        jCheckBoxMenuItem = new JCheckBoxMenuItem(actionSetAutoDownload);
        jCheckBoxMenuItem.setState(prefs.getBoolean(Main.KEY_AUTO_DOWNLOAD, true));
        menu.add(jCheckBoxMenuItem);

        jCheckBoxMenuItem = new JCheckBoxMenuItem(actionSetShowMessageOnComplete);
        jCheckBoxMenuItem.setState(prefs.getBoolean(Main.KEY_MESSAGE_ON_COMPLETE, true));
        menu.add(jCheckBoxMenuItem);

        jCheckBoxMenuItem = new JCheckBoxMenuItem(actionSetSaveDownloadList);
        jCheckBoxMenuItem.setState(prefs.getBoolean(Main.KEY_SAVE_DOWNLOAD_LIST, false));
        menu.add(jCheckBoxMenuItem);

        menuBar.add(menu);

        window.setJMenuBar(menuBar);

        final JPopupMenu treePopup = new JPopupMenu();
        treePopup.add(actionQueue);
        treePopup.add(actionRemoveLock);

        final JPopupMenu listPopup = new JPopupMenu();
        listPopup.add(actionChooseDownloadPath);
        listPopup.add(actionRemoveSelected);
        listPopup.add(actionStartStop);

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

        startButton = new JButton(actionStartStop);

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

            @Override
            public void mouseExited(MouseEvent e) {
                infoBox.setText("");
            }
        });

        downloadList.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {

                Rectangle listBounds = downloadList.getCellBounds(0, downloadList.getModel().getSize() - 1);

                if (listBounds != null && listBounds.contains(e.getPoint())) {

                    int row = downloadList.locationToIndex(e.getPoint());

                    PVRItem item = downloadList.getModel().getElementAt(row);
                    if (item.isFile()) {
                        infoBox.setText(buildDescription((PVRFile) item));
                        return;
                    }

                }
                infoBox.setText("");
            }
        });

        downloadList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                actionRemoveSelected.setEnabled(false);
                actionChooseDownloadPath.setEnabled(false);
                if (downloadList.getSelectedIndices().length > 0) {
                    actionRemoveSelected.setEnabled(true);
                    if (!main.isDownloading()) {
                        actionChooseDownloadPath.setEnabled(true);
                    }
                }
            }
        });

        displayTree = new JTree();

        displayTree.setModel(pvr);
        displayTree.setCellRenderer(new PVRFileTreeCellRenderer());
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
                int row = displayTree.getRowForLocation(e.getX(), e.getY());
                if (row != -1 && SwingUtilities.isRightMouseButton(e)) {
                    displayTree.addSelectionRow(row);
                }

                maybeShowPopup(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int row = displayTree.getRowForLocation(e.getX(), e.getY());
                if (row == -1) {
                    displayTree.clearSelection();
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    displayTree.addSelectionRow(row);
                }
            }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    treePopup.show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                infoBox.setText("");
            }

        });

        displayTree.addMouseMotionListener(new MouseAdapter() {

            @Override
            public void mouseMoved(MouseEvent e) {
                TreePath path = displayTree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    if (path.getLastPathComponent() instanceof PVRFile) {
                        PVRFile file = (PVRFile) path.getLastPathComponent();

                        infoBox.setText(buildDescription(file));
                        return;
                    }
                }
                infoBox.setText("");
            }

        });

        displayTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                actionQueue.setEnabled(false);
                actionRemoveLock.setEnabled(false);
                TreePath[] selectionPaths = displayTree.getSelectionPaths();
                if (selectionPaths == null) {
                    return;
                }
                for (TreePath p : selectionPaths) {
                    if (((PVRItem) p.getLastPathComponent()).isFile()) {
                        PVRFile file = (PVRFile) p.getLastPathComponent();
                        if (!file.isHighDef()) {
                            actionQueue.setEnabled(true);
                        }
                        if (file.isLocked()) {
                            actionRemoveLock.setEnabled(true);
                        }
                    }
                }

            }
        });

        infoBox = new JTextArea();
        infoBox.setBorder(BorderFactory.createEmptyBorder(INFOBOX_PADDING, INFOBOX_PADDING, INFOBOX_PADDING, INFOBOX_PADDING));
        infoBox.setEditable(false);
        infoBox.setLineWrap(true);
        infoBox.setWrapStyleWord(true);

        horizontalSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(displayTree), new JScrollPane(downloadList));
        verticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, horizontalSplitPane, infoBox);

        horizontalSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                prefs.putInt(Main.KEY_HORIZONTAL_DIVIDER_LOCATION, (Integer) evt.getNewValue());
            }
        });

        verticalSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                prefs.putInt(Main.KEY_VERTICAL_DIVIDER_LOCATION, (Integer) evt.getNewValue());
            }
        });

        window.setLayout(new BorderLayout());
        window.add(verticalSplitPane, BorderLayout.CENTER);
        window.add(statusPanel, BorderLayout.SOUTH);

        if (SystemTray.isSupported()) {
            final JPopupMenu trayPopup = new JPopupMenu();

            trayPopup.add(actionRestore);
            trayPopup.add(actionStartStop);
            trayPopup.add(actionQuit);

            Image icon = getTrayIconImage(icons.get(ICON_DISCONNECTED));
            if (icon != null) {
                trayIcon = new TrayIcon(icon, "Media Browser");

                trayIcon.setImageAutoSize(false);

                trayIcon.setActionCommand(ACTION_TRAY);
                trayIcon.addActionListener(main);

                // Addapted from http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6285881
                trayIcon.addMouseListener(new MouseAdapter() {

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        maybeShowPopup(e);
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        maybeShowPopup(e);
                    }

                    private void maybeShowPopup(MouseEvent e) {
                        log.debug("Mouse release {}", e);
                        if (e.isPopupTrigger()) {
                            trayPopup.setLocation(e.getX(), e.getY());
                            trayPopup.setInvoker(trayPopup);
                            trayPopup.setVisible(true);
                        }
                    }

                });

                try {
                    SystemTray.getSystemTray().add(trayIcon);
                } catch (AWTException ex) {
                    log.error("Can't add system tray icon: {}", ex.getMessage(), ex);
                }
            } else {
                trayIcon = null;

            }
        } else {
            trayIcon = null;
        }
    }

    /**
     * Loads the window position from preferences, and shows it.
     */
    void showWindow() {
        window.pack();

        window.setBounds(new Rectangle(
                prefs.getInt(Main.KEY_FRAME_LEFT, 0),
                prefs.getInt(Main.KEY_FRAME_TOP, 0),
                prefs.getInt(Main.KEY_FRAME_WIDTH, 640),
                prefs.getInt(Main.KEY_FRAME_HEIGHT, 480)
        ));

        horizontalSplitPane.setDividerLocation(prefs.getInt(Main.KEY_HORIZONTAL_DIVIDER_LOCATION, window.getWidth() / 2));
        verticalSplitPane.setDividerLocation(prefs.getInt(Main.KEY_VERTICAL_DIVIDER_LOCATION, window.getHeight() - (window.getHeight() / 10)));
        window.setState(JFrame.NORMAL);
        window.setVisible(true);
    }

    /**
     * Hides and kills the window, and removes the system tray icon.
     */
    void stop() {
        window.setVisible(false);
        window.dispose();
        if (SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    /**
     * Sets the message in the status bar.
     *
     * @param status
     */
    void setStatus(final String status) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                statusLabel.setText(status);
            }
        });
    }

    /**
     * Sets the message in the tray icon tool tip
     *
     * @param message
     */
    void setTrayIconToolTip(final String message) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                trayIcon.setToolTip(message);
            }
        });
    }

    /**
     * Shows a popup message from the tray icon.
     *
     * @param title String title of the popup.
     * @param body String body of the popup.
     * @param type TrayIcon.MessageType type of message (for the icon used)
     */
    void showPopupMessage(String title, String body, TrayIcon.MessageType type) {
        trayIcon.displayMessage(title, body, type);
    }

    /**
     * Sets the window and tray icon color.
     *
     * <p>
     * Use one of the {@code ICON_*} constants.
     *
     * @param color
     */
    void setIconColor(String color) {
        window.setIconImages(icons.get(color));
        if (SystemTray.isSupported()) {
            trayIcon.setImage(getTrayIconImage(icons.get(color)));
        }
    }

    /**
     * Show a {@link javax.swing.JFileChooser} to pick a directory.
     *
     * @param base File starting directory for the chooser
     * @return File chosen directory.
     */
    File showDirectoryChooser(File base) {
        final JFileChooser fc = new JFileChooser(base);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Choose download folder");

        int returnVal = fc.showOpenDialog(window);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            return fc.getSelectedFile();
        } else {
            return null;
        }
    }

    /**
     * Get the list of currently selected items from the tree.
     *
     * @return List of PVRFile selected items.
     */
    List<PVRFile> getTreeSelected() {
        List<PVRFile> result = new ArrayList<>();
        TreePath[] selectionPaths = displayTree.getSelectionPaths();
        if (selectionPaths != null) {
            for (TreePath p : selectionPaths) {
                PVR.PVRItem item = (PVR.PVRItem) p.getLastPathComponent();
                if (item.isFile()) {
                    result.add((PVRFile) item);
                }
            }
        }

        return result;
    }

    /**
     * Get the list of currently selected items in the download list.
     *
     * @return List of PVRFile selected items.
     */
    List<PVRFile> getListSelected() {
        return downloadList.getSelectedValuesList();
    }

    /**
     * Sets the state of the StartAction.
     *
     * <p>
     * The action can be either enabled or disabled, and should be disabled when
     * the queue is empty, enabled otherwise, and can be showing either "Start
     * downloading" (when there are no downloads running) or "Stop downloading"
     * (when there are downloads running).
     *
     * @param enabled boolean true if there are files queued, false otherwise.
     * @param downloading boolean true if there is an active download, false
     * otherwise.
     */
    void setStartActionStatus(boolean enabled, boolean downloading) {
        actionStartStop.setEnabled(enabled);
        if (downloading) {
            actionStartStop.putValue(Action.NAME, "Stop downloading");
        } else {
            actionStartStop.putValue(Action.NAME, "Start donwloading");
        }
    }

    /**
     * True if the top level window is visible, false otherwise.
     *
     * @return boolean true if the top level window is visible.
     */
    boolean isVisible() {
        return window.isVisible();
    }

    void refresh() {
        window.revalidate();
    }

    private Image getTrayIconImage(List<Image> applicationIcons) {
        Dimension trayIconSize = SystemTray.getSystemTray().getTrayIconSize();

        for (int i = 0; i < ICON_SIZES.length; i += 1) {
            if (trayIconSize.width == ICON_SIZES[i]) {
                return applicationIcons.get(i);
            }
        }

        return null;
    }

    private List<Image> loadIcons(String color) {
        List<Image> result = new ArrayList<>();

        for (int i = 0; i < ICON_SIZES.length; i += 1) {
            String path = String.format("/icons/PVR Icon %s %dx%d.png", color, ICON_SIZES[i], ICON_SIZES[i]);
            URL imageURL = UI.class.getResource(path);

            if (imageURL != null) {
                try {
                    result.add(ImageIO.read(imageURL));
                } catch (IOException ex) {
                    log.error("Can't load image {}: {}", imageURL, ex.getMessage(), ex);
                }
            } else {
                log.error("Can't find resource for {}", path);
            }
        }

        return result;
    }

    private String buildDescription(PVRFile file) {

        if (file == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        if (file.isFtpScanned()) {

            String title = file.getTitle();
            String desc = file.getDescription();

            if (title.endsWith("...") & desc.startsWith("...")) {
                result.append(title.substring(0, title.length() - 3));
                result.append(" ");
                result.append(desc.substring(3));
            } else {
                result.append(title).append(": ").append(desc);
            }

            if (file.isHighDef() && !desc.contains("[HD]")) {
                result.append(" [HD]");
            }
            if (file.isLocked()) {
                result.append(" [locked]");
            }

            result.append("\n");

            result.append("Channel: ").append(file.getChannelName()).append("\n");
            result.append("Start: ").append(PVRFileTreeCellRenderer.DISPLAY_DATE.print(file.getStartTime())).append("\n");
            result.append("End: ").append(PVRFileTreeCellRenderer.DISPLAY_DATE.print(file.getEndTime())).append("\n");
            result.append("Duration: ").append(PVR.PERIOD_FORMAT.print(file.getLength().toPeriod())).append("\n");

        } else {
            result.append(file.getTitle()).append("\n");
            result.append("(Still scanning)");
        }
        return result.toString();
    }

}

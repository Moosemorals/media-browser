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

    static final String ICON_CONNECTED = "Blue";
    static final String ICON_DISCONNECTED = "Grey";
    static final String ICON_DOWNLOADING = "Red";
    static final String ICON_ERROR = "Error";

    private static final String[] ICON_COLORS = {ICON_DISCONNECTED, ICON_CONNECTED, ICON_DOWNLOADING, ICON_ERROR};
    private static final int[] ICON_SIZES = {32, 24, 20, 16};

    private final Map<String, List<Image>> icons;
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

    private final Action startStopAction, queueAction, removeLockAction, chooseDefaultDownloadPathAction,
            chooseDownloadPathAction, removeSelectedAction, quitAction, setMinimiseToTrayAction,
            setAutoDownloadAction, setSaveDownloadListAction, setShowMessageOnCompleteAction;

    UI(Main m) {

        this.main = m;
        prefs = main.getPreferences();

        setShowMessageOnCompleteAction = new PreferenceAction(prefs, "Show completed notification", Main.KEY_MESSAGE_ON_COMPLETE);
        setSaveDownloadListAction = new PreferenceAction(prefs, "Save download queue", Main.KEY_SAVE_DOWNLOAD_LIST);
        setAutoDownloadAction = new PreferenceAction(prefs, "Automaticaly download next", Main.KEY_AUTO_DOWNLOAD);
        setMinimiseToTrayAction = new PreferenceAction(prefs, "Automaticaly download next", Main.KEY_MINIMISE_TO_TRAY);

        chooseDefaultDownloadPathAction = new LocalAction(main, "Set default download folder", ACTION_CHOOSE_DEFAULT);
        chooseDownloadPathAction = new LocalAction(main, "Set download folder", ACTION_CHOOSE);
        quitAction = new LocalAction(main, "Exit", ACTION_QUIT);
        queueAction = new LocalAction(main, "Queue selected", ACTION_QUEUE);
        removeLockAction = new LocalAction(main, "Remove lock", ACTION_LOCK);
        removeSelectedAction = new LocalAction(main, "Remove from queue", ACTION_REMOVE);
        startStopAction = new LocalAction(main, "Start downloading", ACTION_START_STOP);

        pvr = main.getPVR();
        downloader = main.getDownloadManager();

        queueAction.setEnabled(false);
        removeLockAction.setEnabled(false);
        removeSelectedAction.setEnabled(false);
        chooseDownloadPathAction.setEnabled(false);

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

            Image icon = getTrayIconImage(icons.get(ICON_DISCONNECTED));
            if (icon != null) {
                trayIcon = new TrayIcon(icon, "Media Browser", trayPopup);

                trayIcon.setImageAutoSize(false);

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

        splitPane.setDividerLocation(prefs.getInt(Main.KEY_DIVIDER_LOCATION, window.getWidth() / 2));
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
        startStopAction.setEnabled(enabled);
        if (downloading) {
            startStopAction.putValue(Action.NAME, "Stop downloading");
        } else {
            startStopAction.putValue(Action.NAME, "Start donwloading");
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
        window.repaint();
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

}

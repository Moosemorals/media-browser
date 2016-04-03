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
package com.moosemorals.mediabrowser.ui;

import com.moosemorals.mediabrowser.PVR;
import static com.moosemorals.mediabrowser.PVR.PERIOD_FORMAT;
import com.moosemorals.mediabrowser.PVRFile;
import com.moosemorals.mediabrowser.PVRFolder;
import com.moosemorals.mediabrowser.PVRItem;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.tree.TreeCellRenderer;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draw an entry in the tree.
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
class PVRFileTreeCellRenderer extends PVRCellRenderer implements TreeCellRenderer {

    private static final int LOCK_PADDING = 4;

    private static final Icon HD_ICON = loadIcon("/icons/HD.png", 1);
    private static final Icon LOCK_ICON = loadIcon("/icons/locked.png", 0.65);

    private static Icon loadIcon(String filename, double scale) {
        URL target = PVRFileTreeCellRenderer.class.getResource(filename);
        if (target == null) {
            throw new RuntimeException("Can't load image from [" + filename + "]: Not found");
        }

        BufferedImage raw;
        try {
            raw = ImageIO.read(target);
        } catch (IOException ex) {
            throw new RuntimeException("Can't load image from [" + filename + "]: " + ex.getMessage(), ex);
        }

        if (scale == 1) {
            return new ImageIcon(raw);
        } else {
            return new ImageIcon(raw.getScaledInstance((int) (raw.getWidth() * scale), -1, Image.SCALE_SMOOTH));
        }

    }

    private Icon buildLeafIcon() {

        Icon leaf = UIManager.getIcon("Tree.leafIcon");

        if (leaf == null) {
            return null;
        }

        BufferedImage i = new BufferedImage(HD_ICON.getIconWidth(), HD_ICON.getIconHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = i.createGraphics();

        int Xoffset = (HD_ICON.getIconWidth() - leaf.getIconWidth()) / 2;
        int Yoffset = (HD_ICON.getIconHeight() - leaf.getIconHeight()) / 2;

        leaf.paintIcon(this, g, Xoffset, Yoffset);

        g.dispose();
        return new ImageIcon(i);
    }

    private final Logger log = LoggerFactory.getLogger(PVRFileTreeCellRenderer.class);

    private final JLabel text, lockIcon;
    private final Icon leafIcon;

    PVRFileTreeCellRenderer() {
        text = new JLabel();
        lockIcon = new JLabel();

        leafIcon = buildLeafIcon();

        lockIcon.setBorder(BorderFactory.createEmptyBorder(0, LOCK_PADDING, 0, 0));

        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

        add(text);
        add(lockIcon);

    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean isExpanded, boolean leaf, int row, boolean hasFocus) {

        if (isSelected) {
            setBackground(UIManager.getColor("Tree.selectionBackground"));
            text.setForeground(UIManager.getColor("Tree.selectionForeground"));
        } else {
            setBackground(UIManager.getColor("Tree.textBackground"));
            text.setForeground(UIManager.getColor("Tree.textForeground"));
        }

        PVRItem item = (PVRItem) value;
        if (item.isFile()) {
            PVRFile file = (PVRFile) item;

            if (file.isHighDef()) {
                text.setIcon(HD_ICON);
            } else {
                text.setIcon(leafIcon);
            }

            Duration length = new Duration(file.getStartTime(), file.getEndTime());

            StringBuilder title = new StringBuilder()
                    .append(file.getTitle())
                    .append(": ")
                    .append(PVR.DISPLAY_DATE_AND_TIME.print(file.getStartTime()))
                    .append(" (")
                    .append(PERIOD_FORMAT.print(length.toPeriod()))
                    .append(") ")
                    .append(PVR.humanReadableSize(file.getSize()));

            if (file.isLocked()) {
                setIcon(lockIcon, LOCK_ICON);
            } else {
                setIcon(lockIcon, null);
            }

            text.setText(title.toString());

            if (!file.isDlnaScanned()) {
                float[] colorComponents = text.getForeground().getRGBComponents(null);
                text.setForeground(new Color(colorComponents[0], colorComponents[1], colorComponents[2], 0.5f));
            }
        } else {
            PVRFolder folder = (PVRFolder) item;

            if (isExpanded) {
                text.setIcon(UIManager.getIcon("Tree.openIcon"));
            } else {
                text.setIcon(UIManager.getIcon("Tree.closedIcon"));
            }

            setIcon(lockIcon, null);

            StringBuilder title = new StringBuilder()
                    .append(folder.getRemoteFilename())
                    .append(": ")
                    .append(PVR.humanReadableSize(folder.getSize()))
                    .append(" (")
                    .append(folder.getChildCount())
                    .append(" item")
                    .append(folder.getChildCount() == 1 ? "" : "s")
                    .append(")");

            text.setText(title.toString());
        }

        Border padding = BorderFactory.createEmptyBorder(1, 0, 1, 0);

        Border select;
        if (hasFocus) {
            select = BorderFactory.createLineBorder(Color.black);
        } else {
            select = BorderFactory.createEmptyBorder(1, 1, 1, 1);
        }

        setBorder(BorderFactory.createCompoundBorder(padding, select));

        Dimension preferredSize = getPreferredSize();

        preferredSize.width = tree.getWidth();

        setSize(preferredSize);

        return this;
    }

    /**
     * Set the icon on a label, and set the labels size. If the icon is null,
     * set size to zero.
     *
     * @param label JLabel target label
     * @param icon Icon icon, can be null.
     */
    private void setIcon(JLabel label, Icon icon) {
        if (icon != null) {
            label.setIcon(icon);
            label.setSize(icon.getIconWidth() + LOCK_PADDING, getHeight());
        } else {
            label.setIcon(null);
            label.setSize(0, 0);
        }
    }

}

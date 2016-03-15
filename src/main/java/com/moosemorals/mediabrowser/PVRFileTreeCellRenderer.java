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

import static com.moosemorals.mediabrowser.PVR.DATE_FORMAT;
import static com.moosemorals.mediabrowser.PVR.PERIOD_FORMAT;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.TreeCellRenderer;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draw an entry in the tree.
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
class PVRFileTreeCellRenderer extends JComponent implements TreeCellRenderer {

    private static final Icon HD_ICON = loadIcon("/icons/HD.png", 1);
    private static final Icon LOCK_ICON = loadIcon("/icons/locked.png", 0.75);

    static Icon loadIcon(String filename, double scale) {
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

    private final Logger log = LoggerFactory.getLogger(PVRFileTreeCellRenderer.class);

    private final JLabel text, hdIcon, lockIcon;

    private final JComponent[] components;

    PVRFileTreeCellRenderer() {
        text = new JLabel();
        hdIcon = new JLabel();
        lockIcon = new JLabel();

        this.components = new JComponent[]{text, hdIcon, lockIcon};

        for (JComponent c : components) {
            c.setOpaque(true);
        }

        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

        add(text);
        add(hdIcon);
        add(lockIcon);

    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean isExpanded, boolean leaf, int row, boolean hasFocus) {

        if (leaf) {
            text.setIcon(UIManager.getIcon("Tree.leafIcon"));
        } else if (isExpanded) {
            text.setIcon(UIManager.getIcon("Tree.openIcon"));
        } else {
            text.setIcon(UIManager.getIcon("Tree.closedIcon"));
        }

        PVR.PVRItem item = (PVR.PVRItem) value;
        if (item.isFile()) {
            PVR.PVRFile file = (PVR.PVRFile) item;

            Duration length = new Duration(file.getStartTime(), file.getEndTime());

            StringBuilder title = new StringBuilder()
                    .append(file.getTitle())
                    .append(": ")
                    .append(DATE_FORMAT.print(file.getStartTime()))
                    .append(" (")
                    .append(PERIOD_FORMAT.print(length.toPeriod()))
                    .append(") ")
                    .append(PVR.humanReadableSize(file.getSize()));

            if (file.isHighDef()) {
                setIcon(hdIcon, HD_ICON);
            } else {
                setIcon(hdIcon, null);
            }

            if (file.isLocked()) {
                setIcon(lockIcon, LOCK_ICON);
            } else {
                setIcon(lockIcon, null);
            }

            text.setText(title.toString());

        } else {
            PVR.PVRFolder folder = (PVR.PVRFolder) item;

            setIcon(lockIcon, null);
            setIcon(hdIcon, null);

            StringBuilder title = new StringBuilder()
                    .append(folder.getRemoteFilename())
                    .append(": ");

            if (folder.getSize() >= 0) {
                title.append(PVR.humanReadableSize(folder.getSize()));
            } else {
                title.append("Checking...");
            }

            text.setText(title.toString());
        }

        if (hasFocus) {
            setBorder(BorderFactory.createDashedBorder(Color.BLACK));
        } else if (isSelected) {
            setBorder(BorderFactory.createLineBorder(UIManager.getColor("Tree.selectionBorderColor"), 1));
        } else {
            setBorder(BorderFactory.createLineBorder(UIManager.getColor("Tree.background"), 1));
        }

        if (isSelected) {

            setBackgrounds(UIManager.getColor("Tree.selectionBackground"));
            text.setForeground(UIManager.getColor("Tree.selectionForeground"));
        } else {

            setBackgrounds(UIManager.getColor("Tree.textBackground"));
            text.setForeground(UIManager.getColor("Tree.textForeground"));
        }

        return this;
    }

    private void setIcon(JLabel label, Icon icon) {
        if (icon != null) {
            label.setIcon(icon);
            label.setSize(icon.getIconWidth(), getHeight());
        } else {
            label.setIcon(null);
            label.setSize(0, 0);
        }
    }

    private void setBackgrounds(Color color) {
        setBackground(color);
        for (JComponent c : components) {
            c.setBackground(color);
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
    }

}

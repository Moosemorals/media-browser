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
package com.fluffypeople.pvrbrowser;

import static com.fluffypeople.pvrbrowser.PVR.DATE_FORMAT;
import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class PVRFileTreeCellRenderer implements TreeCellRenderer {

    private final Logger log = LoggerFactory.getLogger(PVRFileTreeCellRenderer.class);
    private final DefaultTreeCellRenderer defaultTreeCellRenderer;

    public PVRFileTreeCellRenderer() {
        defaultTreeCellRenderer = new DefaultTreeCellRenderer();

        log.debug("Icon is {} x {}", defaultTreeCellRenderer.getDefaultLeafIcon().getIconWidth(), defaultTreeCellRenderer.getDefaultLeafIcon().getIconHeight());
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

        JLabel fish = new JLabel();

        if (leaf) {
            fish.setIcon(defaultTreeCellRenderer.getDefaultLeafIcon());
        } else if (expanded) {
            fish.setIcon(defaultTreeCellRenderer.getDefaultOpenIcon());
        } else {
            fish.setIcon(defaultTreeCellRenderer.getDefaultClosedIcon());
        }

        PVR.PVRItem item = (PVR.PVRItem) value;
        if (item.isFile()) {
            PVR.PVRFile file = (PVR.PVRFile) item;
            fish.setText(file.getTitle() + ": " + DATE_FORMAT.print(file.getStart()));
        } else {
            PVR.PVRFolder folder = (PVR.PVRFolder) item;
            fish.setText(folder.getFilename());
        }

        if (selected) {
            fish.setBorder(BorderFactory.createDashedBorder(Color.BLACK));
        }

        return fish;
    }

}

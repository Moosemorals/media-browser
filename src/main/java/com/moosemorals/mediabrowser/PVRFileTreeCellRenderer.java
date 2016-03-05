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
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class PVRFileTreeCellRenderer extends JLabel implements TreeCellRenderer {

    private final Logger log = LoggerFactory.getLogger(PVRFileTreeCellRenderer.class);
    private final DefaultTreeCellRenderer defaultTreeCellRenderer;
    private final JPopupMenu popup;

    public PVRFileTreeCellRenderer(JPopupMenu popup) {
        this.popup = popup;
        defaultTreeCellRenderer = new DefaultTreeCellRenderer();

    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

        if (leaf) {
            setIcon(defaultTreeCellRenderer.getDefaultLeafIcon());
        } else if (expanded) {
            setIcon(defaultTreeCellRenderer.getDefaultOpenIcon());
        } else {
            setIcon(defaultTreeCellRenderer.getDefaultClosedIcon());
        }

        PVR.PVRItem item = (PVR.PVRItem) value;
        if (item.isFile()) {
            PVR.PVRFile file = (PVR.PVRFile) item;

            Duration length = new Duration(file.getStart(), file.getEnd());

            StringBuilder title = new StringBuilder()
                    .append(file.getTitle())
                    .append(": ")
                    .append(PVR.humanReadableSize(file.getSize()))
                    .append(" ")
                    .append(DATE_FORMAT.print(file.getStart()))
                    .append(" (")
                    .append(PERIOD_FORMAT.print(length.toPeriod()))
                    .append(")");

            if (file.isHighDef()) {
                title.append(" [HD]");
            }
            if (file.isLocked()) {
                title.append(" [X]");
            }

            setText(title.toString());
        } else {
            PVR.PVRFolder folder = (PVR.PVRFolder) item;

            StringBuilder title = new StringBuilder()
                    .append(folder.getFilename())
                    .append(": ");

            if (folder.getSize() >= 0) {
                title.append(PVR.humanReadableSize(folder.getSize()));
            } else {
                title.append("Checking...");
            }

            setText(title.toString());
        }

        if (selected) {
            setBorder(BorderFactory.createDashedBorder(Color.BLACK));
        } else {
            setBorder(null);
        }

        return this;
    }

}

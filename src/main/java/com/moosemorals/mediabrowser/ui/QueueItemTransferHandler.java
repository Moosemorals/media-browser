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

import com.moosemorals.mediabrowser.DownloadManager;
import com.moosemorals.mediabrowser.DownloadManager.QueueItem;
import com.moosemorals.mediabrowser.PVRFile;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Part of the Drag and Drop infrastructure.
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
class QueueItemTransferHandler extends TransferHandler {

    private final Logger log = LoggerFactory.getLogger(QueueItemTransferHandler.class);

    @Override
    public boolean importData(TransferSupport info) {
        if (!canImport(info)) {
            return false;
        }

        Component component = info.getComponent();
        if (component instanceof JList) {
            JList<QueueItem> list = (JList<QueueItem>) component;

            Point dropPoint = info.getDropLocation().getDropPoint();
            int row = list.locationToIndex(dropPoint);

            DownloadManager dlManager = (DownloadManager) list.getModel();

            try {
                List<QueueItem> files = (List<QueueItem>) info.getTransferable().getTransferData(QueueItemTransferable.QueueItemFlavor);

                if (info.getDropAction() == MOVE) {
                    dlManager.moveFiles(row, files);
                } else {
                    if (row == -1) {
                        // Target list is empty, add files at the start
                        row = 0;
                    } else {
                        // We're gven the same answer for "Just before the last row"
                        // and "Anywhere after the last row", so check if the
                        // point is actualy *within* the last row, and asuume
                        // that we're dropping after if its not.
                        Rectangle bounds = list.getCellBounds(row, row);
                        if (!bounds.contains(dropPoint)) {
                            row += 1;
                        }
                    }

                    dlManager.insert(row, files);
                }
                return true;
            } catch (UnsupportedFlavorException | IOException ex) {
                log.warn("Can't do transfer: {}", ex.getMessage(), ex);
                return false;
            }
        }
        log.warn("Target not a list");
        return false;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        if (c instanceof JList) {
            QueueItemTransferable pvrFileTransferable = new QueueItemTransferable(((JList<QueueItem>) c).getSelectedValuesList());

            ((JList<QueueItem>) c).clearSelection();

            return pvrFileTransferable;
        } else if (c instanceof JTree) {
            List<QueueItem> files = new ArrayList<>();
            TreePath[] selectionPaths = ((JTree) c).getSelectionPaths();
            if (selectionPaths == null) {
                return null;
            }
            DownloadManager dm = DownloadManager.getInstance();
            for (TreePath p : selectionPaths) {
                Object o = p.getLastPathComponent();
                if (o instanceof PVRFile) {
                    PVRFile target = (PVRFile) o;

                    if (dm.isQueuable(target)) {
                        QueueItem item = dm.createQueueItem(target, null);
                        item.checkTarget();
                        files.add(item);
                    }
                }
            }
            ((JTree) c).clearSelection();
            if (files.size() > 0) {
                return new QueueItemTransferable(files);
            } else {
                log.debug("Nothing to transfer");
                return null;
            }
        } else {
            log.warn("Unexpected data souce component {}", c);
            return null;
        }
    }

    @Override
    public int getSourceActions(JComponent c) {
        if (c instanceof JList) {
            return MOVE;
        } else {
            return COPY;
        }
    }

    @Override
    public boolean canImport(TransferSupport info) {
        return info.isDataFlavorSupported(QueueItemTransferable.QueueItemFlavor);
    }

}

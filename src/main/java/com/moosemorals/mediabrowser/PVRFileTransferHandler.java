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
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class PVRFileTransferHandler extends TransferHandler {

    private final Logger log = LoggerFactory.getLogger(PVRFileTransferHandler.class);

    @Override
    public boolean importData(TransferSupport info) {
        log.debug("import data");
        if (!canImport(info)) {
            return false;
        }

        Component component = info.getComponent();
        if (component instanceof JList) {
            JList<PVRFile> list = (JList<PVRFile>) component;

            Point dropPoint = info.getDropLocation().getDropPoint();
            int row = list.locationToIndex(dropPoint);

            try {
                List<PVRFile> files = (List<PVRFile>) info.getTransferable().getTransferData(PVRFileTransferable.PVRFileFlavor);

                DownloadManager dlManager = (DownloadManager) list.getModel();

                if (info.getDropAction() == MOVE) {
                    log.debug("MOVE");
                    dlManager.dropFiles(row, files);
                } else {
                    log.debug("COPY");

                    if (row == -1) {
                        row = 0;
                    } else {
                        Rectangle bounds = list.getCellBounds(row, row);

                        if (!bounds.contains(dropPoint)) {
                            log.debug("Dropping after last entry");
                            row += 1;
                        }
                    }

                    dlManager.insertFiles(row, files);
                }
                return true;
            } catch (UnsupportedFlavorException | IOException ex) {
                log.debug("Can't do transfer: {}", ex.getMessage(), ex);
                return false;
            }
        }
        log.warn("Target not a list");
        return false;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        if (c instanceof JList) {
            return new PVRFileTransferable(((JList<PVRFile>) c).getSelectedValuesList());
        } else if (c instanceof JTree) {
            List<PVRFile> files = new ArrayList<>();
            for (TreePath p : ((JTree) c).getSelectionPaths()) {
                Object o = p.getLastPathComponent();
                if (o instanceof PVRFile) {
                    files.add((PVRFile) o);
                }
            }
            return new PVRFileTransferable(files);
        } else {
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
        return info.isDataFlavorSupported(PVRFileTransferable.PVRFileFlavor);
    }

}

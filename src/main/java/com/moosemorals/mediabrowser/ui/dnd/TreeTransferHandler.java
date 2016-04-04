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
package com.moosemorals.mediabrowser.ui.dnd;

import com.moosemorals.mediabrowser.PVR;
import com.moosemorals.mediabrowser.PVRFile;
import com.moosemorals.mediabrowser.PVRFolder;
import com.moosemorals.mediabrowser.PVRItem;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Part of the Drag and Drop infrastructure.
 *
 * Trees can import/export lists of PVRItems (and by import, I mean move
 * internally)
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
public class TreeTransferHandler extends TransferHandler {

    private final Logger log = LoggerFactory.getLogger(TreeTransferHandler.class);

    private final PVR pvr;

    public TreeTransferHandler(PVR pvr) {
        this.pvr = pvr;
    }

    @Override
    public boolean importData(TransferSupport info) {
        if (!canImport(info)) {
            return false;
        }

        if (info.getComponent() instanceof JTree) {

            try {
                List<PVRItem> files = (List<PVRItem>) info.getTransferable().getTransferData(PVRItemTransferable.PVRItemFlavor);
                JTree.DropLocation dropLocation = (JTree.DropLocation) info.getDropLocation();

                PVRFolder target;
                if (dropLocation != null) {
                    target = (PVRFolder) dropLocation.getPath().getLastPathComponent();
                } else {
                    return false;
                }

                log.debug("Moving {} to {}", files, target);

                return true;
            } catch (UnsupportedFlavorException | IOException ex) {
                log.warn("Can't do transfer: {}", ex.getMessage(), ex);
                return false;
            }

        }
        log.warn("Target not a tree");
        return false;
    }

    @Override
    protected Transferable createTransferable(JComponent source) {
        if (source instanceof JTree) {
            List<PVRItem> files = new ArrayList<>();
            TreePath[] selectionPaths = ((JTree) source).getSelectionPaths();
            if (selectionPaths == null) {
                return null;
            }

            for (TreePath p : selectionPaths) {
                Object o = p.getLastPathComponent();
                if (o instanceof PVRFile) {
                    files.add((PVRFile) o);
                }
            }

            ((JTree) source).clearSelection();

            if (files.size() > 0) {
                return new PVRItemTransferable(files);
            } else {
                log.debug("Nothing to transfer");
                return null;
            }
        } else {
            log.warn("Unexpected data souce component {}", source);
            return null;
        }
    }

    @Override
    public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE;
    }

    @Override
    public boolean canImport(TransferSupport info) {
        if (!info.isDrop()) {
            return false;
        }

        if (!info.isDataFlavorSupported(PVRItemTransferable.PVRItemFlavor)) {
            return false;
        }

        if (info.getComponent() instanceof JTree) {

            if ((MOVE & info.getUserDropAction()) != MOVE) {
                return false;
            }

            JTree.DropLocation dl = (JTree.DropLocation) info.getDropLocation();

            TreePath p = dl.getPath();

            if (p == null) {
                return false;
            }

            return p.getLastPathComponent() instanceof PVRFolder;
        }
        return false;
    }

}

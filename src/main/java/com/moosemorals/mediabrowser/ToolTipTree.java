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
import java.awt.event.MouseEvent;
import javax.swing.JToolTip;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link javax.swing.JTree} that has sensible tooltips.
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class ToolTipTree extends JTree {

    private final Logger log = LoggerFactory.getLogger(ToolTipTree.class);

    public ToolTipTree() {
        super();
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    @Override
    public JToolTip createToolTip() {
        return new WrappingToolTip();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        TreePath path = getPathForLocation(event.getX(), event.getY());
        if (path != null) {
            PVRItem item = (PVRItem) path.getLastPathComponent();

            if (item.isFile()) {
                return ((PVRFile) item).getDescription();
            }
        }
        return null;
    }

}

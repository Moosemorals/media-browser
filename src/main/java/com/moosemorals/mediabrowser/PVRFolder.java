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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a folder on the remote device. May have children, which will
 * either be files or folders (which may have their own children).
 */
public class PVRFolder extends PVRItem {

    private final Logger log = LoggerFactory.getLogger(PVRFolder.class);

    final List<PVRItem> children;

    protected PVRFolder(PVRFolder parent, String path, String filename) {
        super(parent, path, filename);
        this.children = new ArrayList<>();
    }

    @Override
    public int compareTo(PVRItem o) {
        if (o.isFile()) {
            // Folders go first
            return -1;
        } else {
            return getRemoteFilename().compareTo(o.getRemoteFilename());
        }
    }

    @Override
    public boolean isFolder() {
        return true;
    }

    @Override
    public boolean isFile() {
        return false;
    }

    @Override
    public long getSize() {
        synchronized (children) {
            long size = 0;
            for (PVRItem child : children) {
                size += child.getSize();
            }
            return size;
        }
    }

    /**
     * Add a child item to this folder. Doesn't check for duplicates.
     *
     * @param child PVRItem child to add
     */
    int addChild(PVRItem child) {
        synchronized (children) {
            children.add(child);
            child.setParent(this);
            child.setTreePath(treePath.pathByAddingChild(child));
            Collections.sort(children);
            return children.indexOf(child);
        }
    }

    /**
     * Get a child by index.
     *
     * @param index int index of the child. No bounds checking is done.
     * @return PVRItem at index.
     */
    public PVRItem getChild(int index) {
        synchronized (children) {
            return children.get(index);
        }
    }

    public int getChildIndex(PVRItem child) {
        synchronized (children) {
            return children.indexOf(child);
        }
    }

    public void removeChild(PVRItem child) {
        synchronized (children) {
            children.remove(child);
        }
    }

    /**
     * Get the number of children of this Folder.
     *
     * @return int number of children.
     */
    public int getChildCount() {
        synchronized (children) {
            return children.size();
        }
    }

    /**
     * Delete the children of this item, and their children too.
     */
    public void clearChildren() {
        synchronized (children) {
            for (Iterator<PVRItem> it = children.iterator(); it.hasNext();) {
                PVRItem item = it.next();
                if (item.isFolder()) {
                    ((PVRFolder) item).clearChildren();
                }
                it.remove();
            }
        }
    }

}

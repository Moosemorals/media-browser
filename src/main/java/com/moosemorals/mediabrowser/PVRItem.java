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

import java.util.Objects;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parent class of PVRFile and PVRFolder. Allows Folders to have Files and
 * Folders as children.
 */
public abstract class PVRItem implements Comparable<PVRItem> {

    private final Logger log = LoggerFactory.getLogger(PVRItem.class);

    protected String remoteFilename;
    protected final String remotePath;
    protected final PVRFolder parent;
    protected final TreePath treePath;

    @SuppressWarnings(value = "LeakingThisInConstructor")
    protected PVRItem(PVRFolder parent, String remotePath, String remoteFilename) {
        this.parent = parent;
        this.remoteFilename = remoteFilename;
        this.remotePath = remotePath;
        if (parent != null) {
            Object[] parentPath = parent.getTreePath().getPath();
            Object[] myPath = new Object[parentPath.length + 1];
            System.arraycopy(parentPath, 0, myPath, 0, parentPath.length);
            myPath[myPath.length - 1] = this;
            treePath = new TreePath(myPath);
        } else {
            // root node;
            treePath = new TreePath(this);
        }
    } // root node;

    @Override
    public abstract int compareTo(PVRItem other);

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(this.remoteFilename);
        hash = 89 * hash + Objects.hashCode(this.remotePath);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PVRItem other = (PVRItem) obj;
        return this.remotePath.equals(other.remotePath) && this.remoteFilename.equals(other.remoteFilename);
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("{")
                .append(remotePath)
                .append("/")
                .append(remoteFilename)
                .append(":")
                .append(isFile() ? " file" : " folder")
                .append("}")
                .toString();
    }

    /**
     * Returns true if this is a PVRFile.
     *
     * @return boolean
     */
    public abstract boolean isFile();

    /**
     * Returns true if this is a PVRFolder.
     *
     * @return
     */
    public abstract boolean isFolder();

    /**
     * Returns the size of this item. Files have a simple size, folders include
     * the size of all their children (recursivley).
     *
     * @return
     */
    public abstract long getSize();

    /**
     * Get the filename (last path segment) as reported by the PVR.
     *
     * @return
     */
    public String getRemoteFilename() {
        return remoteFilename;
    }

    void setRemoteFilename(String remoteFilename) {
        this.remoteFilename = remoteFilename;
    }

    /**
     * Get the path reported by the PVR. Note that you'll need to add FTP_ROOT
     * to the front if you're using it for FTP.
     *
     * @return String path reported by DLNA.
     */
    public String getRemotePath() {
        return remotePath;
    }

    /**
     * Get parent Folder. Will return null for the root folder.
     *
     * @return PVRFolder parent folder, or null for the root.
     */
    public PVRFolder getParent() {
        return parent;
    }

    /**
     * Get TreePath. This is set in the constructor, and never changes. Should
     * never be null.
     *
     * @return TreePath
     */
    public TreePath getTreePath() {
        return treePath;
    }

}

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Models a remote PVR.
 *
 * PVRs have folders that contain files. Files have a bunch of attributes.
 * (Folders also have attributes
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
public class PVR {

    private final Logger log = LoggerFactory.getLogger(PVR.class);

    public static abstract class PVRItem implements Comparable<PVRItem> {

        protected final String name;
        protected final String path;

        public PVRItem(String name, String path) {
            this.name = name;
            this.path = path;

        }

        @Override
        public int compareTo(PVRItem o) {
            if (isFolder() && o.isFile()) {
                // Folders go first
                return -1;
            } else if (isFile() && o.isFolder()) {
                return 1;
            } else {
                return getName().compareTo(o.getName());
            }
        }

        public abstract boolean isFile();

        public abstract boolean isFolder();

        /**
         * Get human readable/display name.
         *
         * @return
         */
        public String getName() {
            return name;
        }

        /**
         * Get path on PVR filesystem
         *
         * @return
         */
        public String getPath() {
            return path;
        }
    }

    public static class PVRFolder extends PVRItem {

        private final List<PVRItem> children;

        public PVRFolder(String path, String name) {
            super(path, name);
            this.children = new ArrayList<>();
        }

        @Override
        public boolean isFolder() {
            return true;
        }

        @Override
        public boolean isFile() {
            return false;
        }

        public void addChild(PVRItem child) {
            synchronized (children) {
                children.add(child);
            }
        }

        public List<PVRItem> getChildren() {
            synchronized (children) {
                return Collections.unmodifiableList(children);
            }
        }

    }

    public static class PVRFile extends PVRItem {

        private long size = -1;
        private long downloaded = -1;
        private String downloadURL = null;
        private String description = "";

        @Override
        public boolean isFolder() {
            return false;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        public PVRFile(String path, String name) {
            super(path, name);
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public long getDownloaded() {
            return downloaded;
        }

        public void setDownloaded(long downloaded) {
            this.downloaded = downloaded;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDownloadURL() {
            return downloadURL;
        }

        public void setDownloadURL(String downloadURL) {
            this.downloadURL = downloadURL;
        }

    }

}

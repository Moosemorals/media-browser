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

import com.moosemorals.mediabrowser.DownloadManager.QueueItem;
import com.moosemorals.mediabrowser.PVR;
import com.moosemorals.mediabrowser.PVRFile;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JProgressBar;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Show PVRFile in a JList.
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
class QueueItemListCellRenderer extends PVRCellRenderer implements ListCellRenderer<QueueItem> {

    private static final Dimension progressSize = new Dimension(120, 20);
    private final Logger log = LoggerFactory.getLogger(QueueItemListCellRenderer.class);

    private final JProgressBar progress;
    private final JLabel text;

    QueueItemListCellRenderer() {
        super();

        progress = new JProgressBar();

        progress.setSize(progressSize);
        progress.setPreferredSize(progressSize);
        progress.setMinimum(0);
        progress.setStringPainted(true);
        text = new JLabel();

        GroupLayout group = new GroupLayout(this);

        setLayout(group);

        group.setAutoCreateGaps(true);
        group.setAutoCreateContainerGaps(false);

        group.setHorizontalGroup(
                group.createSequentialGroup()
                .addComponent(progress, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(text)
        );
        group.setVerticalGroup(
                group.createParallelGroup()
                .addComponent(progress, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(text)
        );
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends QueueItem> list, QueueItem item, int index, boolean isSelected, boolean hasFocus) {

        PVRFile file = item.getTarget();

        if (file == null) {
            throw new RuntimeException("File is null!");
        }

        int scaledSize = (int) (file.getSize() / PVR.MEGA);
        int scaledDownload = (int) (file.getDownloaded() / PVR.MEGA);

        progress.setMaximum(scaledSize);
        progress.setValue(scaledDownload);

        String state;
        switch (file.getState()) {
            case Queued:
                state = "Queued";
                break;
            case Downloading:
                state = String.format("%3.0f%%", (scaledDownload / (double) scaledSize) * 100.0);
                break;
            case Error:
                state = String.format("Error - %3.0f%%", (scaledDownload / (double) scaledSize) * 100.0);
                break;
            case Paused:
                state = String.format("Paused - %3.0f%%", (scaledDownload / (double) scaledSize) * 100.0);
                break;
            case Completed:
                state = "Done";
                break;
            case Ready:
            // Shoudn't need this one, drop through to default
            default:
                log.warn("Unexpedted state {}", file.getState());
                state = "Unknown";
        }

        progress.setString(state);

        String title = new StringBuilder()
                .append(file.getTitle())
                .append(" → ")
                .append(file.getLocalPath().getPath())
                .append("/")
                .append(file.getLocalFilename())
                .toString();

        text.setText(title);

        Border padding = BorderFactory.createEmptyBorder(1, 0, 1, 0);

        Border select;
        if (hasFocus) {
            select = BorderFactory.createLineBorder(Color.black);
        } else {
            select = BorderFactory.createEmptyBorder(1, 1, 1, 1);
        }

        setBorder(BorderFactory.createCompoundBorder(padding, select));

        if (isSelected) {
            setBackground(UIManager.getColor("List.selectionBackground"));
            text.setForeground(UIManager.getColor("List.selectionForeground"));
        } else {
            setBackground(UIManager.getColor("List.textBackground"));
            text.setForeground(UIManager.getColor("List.textForeground"));
        }

        return this;
    }

}

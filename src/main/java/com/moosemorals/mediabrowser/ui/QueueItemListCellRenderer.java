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
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JProgressBar;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Show PVRFile in a JList.
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
class QueueItemListCellRenderer extends PVRCellRenderer implements ListCellRenderer<QueueItem> {

    static final Dimension PROGRESS_SIZE = new Dimension(120, 20);
    private final Logger log = LoggerFactory.getLogger(QueueItemListCellRenderer.class);

    private static final Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);
    private static final Border padding = BorderFactory.createEmptyBorder(1, 0, 1, 0);

    private final JProgressBar progress;
    private final JLabel text;

    QueueItemListCellRenderer() {
        super();

        //  setOpaque(true);
        setBorder(noFocusBorder);

        progress = new JProgressBar();

        progress.setSize(PROGRESS_SIZE);
        progress.setPreferredSize(PROGRESS_SIZE);
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

        setComponentOrientation(list.getComponentOrientation());

        Color bg = null;
        Color fg = null;

        JList.DropLocation dropLocation = list.getDropLocation();
        if (dropLocation != null
                && !dropLocation.isInsert()
                && dropLocation.getIndex() == index) {

            bg = UIManager.getColor("List.dropCellBackground");
            fg = UIManager.getColor("List.dropCellForeground");

            isSelected = true;
        }

        if (isSelected) {
            setBackground(bg == null ? list.getSelectionBackground() : bg);
            text.setForeground(fg == null ? list.getSelectionForeground() : fg);
        } else {
            setBackground(list.getBackground());
            text.setForeground(list.getForeground());
        }

        setEnabled(list.isEnabled());
        setFont(list.getFont());

        
        

        PVRFile file = item.getTarget();

        if (file == null) {
            throw new RuntimeException("File is null!");
        }

        int scaledSize = (int) (item.getSize() / PVR.MEGA);
        int scaledDownload = (int) (item.getDownloaded() / PVR.MEGA);
        progress.setIndeterminate(false);
        progress.setMaximum(scaledSize);
        progress.setValue(scaledDownload);

        String state;
        switch (item.getState()) {
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
            case Moving:
                state = String.format("Moving - %3.0f%%", item.getMoveProgress() * 100.0);
                progress.setMaximum(100);
                progress.setValue((int) item.getMoveProgress() * 100);

                break;
            default:
                log.warn("Unexpedted state {}", item.getState());
                state = "Unknown";
        }

        progress.setString(state);

        String title = new StringBuilder()
                .append(file.getTitle())
                .append(" → ")
                .append(item.getLocalPath().getPath())
                .append("/")
                .append(item.getLocalFilename())
                .toString();

        text.setText(title);

        

        Border select = null;
        if (hasFocus) {
            if (isSelected) {
                select = UIManager.getBorder("List.focusSelectedCellHighlightBorder");
            }
            if (select == null) {
                select = UIManager.getBorder("List.focusCellHighlightBorder");
            }
        } else {
            select = noFocusBorder;
        }

        setBorder(BorderFactory.createCompoundBorder(padding, select));

        return this;
    }

}

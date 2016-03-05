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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.ListCellRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Show PVRFile in a JList.
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
public class PVRFileListCellRenderer extends JPanel implements ListCellRenderer<PVRFile> {

    private final Logger log = LoggerFactory.getLogger(PVRFileListCellRenderer.class);

    private static final Dimension progressSize = new Dimension(100, 16);

    private final JProgressBar progress;
    private final JLabel text;
    private final JLabel state;

    public PVRFileListCellRenderer() {
        super();

        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

        text = new JLabel();
        add(text);

        add(Box.createHorizontalGlue());

        state = new JLabel();
        add(state);

        progress = new JProgressBar();

        progress.setMinimumSize(progressSize);
        progress.setMaximumSize(progressSize);
        progress.setMinimum(0);
        progress.setStringPainted(true);
        add(progress);

    }

    @Override
    public Component getListCellRendererComponent(JList<? extends PVRFile> list, PVRFile file, int index, boolean isSelected, boolean cellHasFocus) {

        int scaledSize = (int) (file.getSize() / PVR.MEGA);
        int scaledDownload = (int) (file.getDownloaded() / PVR.MEGA);

        progress.setMaximum(scaledSize);
        progress.setValue(scaledDownload);
        progress.setString(String.format("%3.0f%%", ((double) scaledDownload / (double) scaledSize) * 100.0));

        String title = new StringBuilder()
                .append(file.getTitle())
                .append(" → ")
                .append(file.getDownloadPath())
                .append("/")
                .append(file.getDownloadFilename())
                .toString();

        text.setText(title);

        if (isSelected) {
            setBorder(BorderFactory.createDashedBorder(Color.BLACK));
        } else {
            setBorder(null);
        }

        switch (file.getState()) {
            case Queued:
                state.setText("*");
                break;
            case Downloading:
                state.setText("▶");
                break;
            case Error:
                state.setText("!");
                break;
            case Paused:
                state.setText("P");
                break;
            case Completed:
                state.setText("✔");
                break;
            case Ready:
            // Shoudn't need this one, drop through to default
            default:
                log.warn("Unexpedted state {}", file.getState());
                state.setText("?");
        }

        return this;
    }

}

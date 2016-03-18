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

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Show a window with copyright info and stuff.
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class About {

    private final Logger log = LoggerFactory.getLogger(About.class);

    private final JFrame window;

    About() {

        JButton close = new JButton("OK");

        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                window.dispose();
            }
        });

        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);

        URL aboutUrl = About.class.getResource("/about.html");

        try {
            if (aboutUrl != null) {
                textPane.setPage(aboutUrl);
            } else {
                throw new IOException("Null url");
            }
        } catch (IOException ex) {
            log.error("Can't load about text: {}", ex, ex.getMessage());
        }

        textPane.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    if (Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().browse(e.getURL().toURI());
                        } catch (URISyntaxException | IOException ex) {
                            log.error("Can't open browser to [{}]: {}", e.getDescription(), ex.getMessage(), ex);
                        }
                    } else {
                        log.warn("Can't get desktop object, not supported");
                    }
                }
            }
        });

        window = new JFrame("About Media Browser");

        window.addWindowStateListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                window.dispose();
            }

        });
        window.setResizable(false);
        window.setLayout(new BorderLayout());
        window.add(new JScrollPane(textPane, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        window.add(close, BorderLayout.PAGE_END);
    }

    void start() {
        Point centerPoint = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getCenterPoint();

        window.pack();
        window.setBounds(centerPoint.x - 220, centerPoint.y - 320, 440, 640);
        window.setVisible(true);
    }

}

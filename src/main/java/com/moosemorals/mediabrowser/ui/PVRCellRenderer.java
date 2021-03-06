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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JComponent;
import javax.swing.UIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common parent of Tree and List cell renderers, to hold some shared code.
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
public class PVRCellRenderer extends JComponent {

    private final Logger log = LoggerFactory.getLogger(PVRCellRenderer.class);

    /**
     * Work out the preferred size of this component.
     *
     * <p>
     * Our width is the sum of the width of our child components, plus some
     * padding for the border.</p>
     * <p>
     * Our height is the hight of our tallest child, plus some padding.</p>
     *
     * @return {@link java.awt.Dimension} size of the component
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension size = new Dimension();

        Dimension other;

        for (Component c : getComponents()) {
            other = c.getPreferredSize();
            size.width += other.width;
            if (size.height < other.height) {
                size.height = other.height;
            }
        }

        size.width += 2;
        size.height += 4;
        return size;
    }

    @Override
    public void paintComponent(Graphics g) {
        g.setColor(UIManager.getColor("Tree.background"));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(getBackground());
        g.fillRect(1, 1, getWidth() - 1, getHeight() - 2);
    }

}

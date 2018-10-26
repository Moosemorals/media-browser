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

import com.moosemorals.mediabrowser.Main;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extends AbstractAction to send all ActionEvents to Main.
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
class LocalAction extends AbstractAction {

    private final Logger log = LoggerFactory.getLogger(LocalAction.class);
    private final Main main;

    LocalAction(Main m, String name, String actionCommand) {
        super(name);
        this.main = m;
        putValue(ACTION_COMMAND_KEY, actionCommand);
    }

    LocalAction(Main m, String name, String actionCommand, KeyStroke key) {
        this(m, name, actionCommand);
        putValue(ACCELERATOR_KEY, key); 
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        main.actionPerformed(e);
    }
}

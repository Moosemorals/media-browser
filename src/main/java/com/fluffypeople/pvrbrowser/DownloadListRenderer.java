/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.awt.Component;
import javax.swing.*;

/**
 *
 * @author Osric
 */
public class DownloadListRenderer extends JComponent implements ListCellRenderer<RemoteItem> {

    private final JLabel name = new JLabel();
    private final JProgressBar progress = new JProgressBar();

    @Override
    public Component getListCellRendererComponent(JList<? extends RemoteItem> jlist, RemoteItem e, int i, boolean bln, boolean bln1) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.util.EventListener;

/**
 *
 * @author Osric
 */
public interface StateChangeListener extends EventListener {

    public void stateChanged(DownloadQueueItem source);

}

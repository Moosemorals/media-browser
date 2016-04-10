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
package com.moosemorals.configuration;

import java.util.Iterator;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.apache.commons.configuration2.AbstractConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Map between Java Preferences API and the Apache Commons Configuration API.
 *
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class PreferencesConfiguration extends AbstractConfiguration {

    private final Logger log = LoggerFactory.getLogger(PreferencesConfiguration.class);

    private final Preferences prefs;

    public PreferencesConfiguration(Preferences prefs) {
        this.prefs = prefs;
    }

    @Override
    protected void addPropertyDirect(String key, Object value) {
        if (value instanceof String) {
            prefs.put(key, (String) value);
        } else if (value instanceof Boolean) {
            prefs.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            prefs.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            prefs.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            prefs.putFloat(key, (Float) value);
        } else if (value instanceof byte[]) {
            prefs.putByteArray(key, (byte[]) value);
        } else {
            throw new IllegalArgumentException("Can't store a " + value.getClass());
        }
    }

    @Override
    protected void clearPropertyDirect(String key) {
        prefs.remove(key);
    }

    @Override
    protected Iterator<String> getKeysInternal() {
        try {
            return new Iterator<String>() {
                String[] keys = prefs.keys();

                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < keys.length;
                }

                @Override
                public String next() {
                    return keys[index++];
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("More trouble that its worth, at the moment");
                }
            };

        } catch (BackingStoreException ex) {
            throw new RuntimeException("Can't get list of keys from backing store", ex);
        }

    }

    @Override
    protected Object getPropertyInternal(String key) {
        return prefs.get(key, null);
    }

    @Override
    protected boolean isEmptyInternal() {
        try {
            return prefs.keys().length == 0;
        } catch (BackingStoreException ex) {
            throw new RuntimeException("Can't get list of keys from backing store", ex);
        }
    }

    @Override
    protected boolean containsKeyInternal(String key) {
        try {
            for (String k : prefs.keys()) {
                if (k.equals(key)) {
                    return true;
                }
            }
            return false;
        } catch (BackingStoreException ex) {
            throw new RuntimeException("Can't get list of keys from backing store", ex);
        }
    }

}

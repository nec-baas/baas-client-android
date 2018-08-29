/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

/**
 * 重複なし文字列 ArrayList。
 *
 * <p><strong>本クラスのインスタンスはスレッドセーフではない。</strong></p>
 */
public class NbStringArrayNoDup extends ArrayList<String> implements Serializable {
    public NbStringArrayNoDup() {
        super();
    }

    public NbStringArrayNoDup(Collection<String> coll) {
        super();
        if (coll != null) {
            for (String s : coll) {
                add(s);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized boolean add(String s) {
        if (!contains(s)) {
            return super.add(s);
        }
        return false;
    }
}

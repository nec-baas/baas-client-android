/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.core;

import java.util.List;

/**
 * List コールバック。成功時は実行結果を List で返却する。
 * NbCallback&lt;List&lt;Type&gt;&gt; の省略形。
 * @since 6.5.0
 */
public interface NbListCallback<T> extends NbCallback<List<T>> {
}

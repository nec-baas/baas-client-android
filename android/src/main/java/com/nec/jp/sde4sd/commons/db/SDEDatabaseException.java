/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.jp.sde4sd.commons.db;

import android.database.SQLException;

/**
 * SDE4SDのデータベース部品に関連した、SQLパースあるいはSQL実行時の例外を定義したクラス。
 */
public class SDEDatabaseException extends SQLException {
	/**	 */
	public SDEDatabaseException() {
	}

	/**
	 * @param error エラーメッセージ
	 */
	public SDEDatabaseException(String error) {
		super(error);
	}
}

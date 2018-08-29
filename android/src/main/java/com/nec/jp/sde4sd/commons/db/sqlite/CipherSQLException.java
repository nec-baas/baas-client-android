/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.jp.sde4sd.commons.db.sqlite;

import com.nec.jp.sde4sd.commons.db.SDEDatabaseException;

public class CipherSQLException extends SDEDatabaseException {
	/**
	 * 
	 */
	public CipherSQLException(){
	}
	
	/**
	 * 
	 * @param error
	 */
	public CipherSQLException(String error){
		super(error);
	}
}

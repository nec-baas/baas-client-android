/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.jp.sde4sd.commons.db.sqlite;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class KeyGenerator {
	private static final int HASH_REPEAT_TIMES = 10000;
	private MessageDigest mDigest;

	/**
	 * KeyGenerator作成
	 * @param algorithm 鍵生成アルゴリズム
	 * @throws NoSuchAlgorithmException 
	 */
	private KeyGenerator(String algorithm) throws NoSuchAlgorithmException{
		mDigest = MessageDigest.getInstance(algorithm);
	}
	
	/**
	 * KeyGeneratorオブジェクト作成。
	 * 
	 * @param algorithm 鍵生成アルゴリズム
	 * @return KeyGenerator KeyGeneratorオブジェクト
	 * @throws NoSuchAlgorithmException 
	 */
	public static final KeyGenerator getInstance(String algorithm) throws NoSuchAlgorithmException{
		return new KeyGenerator(algorithm);
	}
	
	/**
	 * 暗号化キーの作成。
	 * 
	 * @param password パスワード(DBの暗号化キーの元になる文字列)
	 * @return 暗号化キー文字列 
	 */
	public final String generateKey(String password){
		if(password == null){
			throw new NullPointerException("password is null");			
		}
		else if(password.equals("")){
			throw new IllegalArgumentException("password is empty");
		}
		
		String keyBase = password + BaseKey.CIPHER_BASE_KEY;

		//指定回数繰り返しハッシュ値を計算
		byte[] values = keyBase.getBytes();
		for(int i = 0; i < HASH_REPEAT_TIMES; i++){
			mDigest.update(values);
			values = mDigest.digest();
		}

		//文字列に変換
		String ret = "x\'" + bytes2String(values) + "\'";
		
		return ret;
	}
	
	/**
	 * バイト配列から文字列への変換。
	 * 
	 * @param values バイト配列
	 * @return 文字列
	 */
	private String bytes2String(byte[] values){
		StringBuffer buf = new StringBuffer();

		for(byte value : values){
			String tmp = Integer.toHexString(value & 0xff);
			if(tmp.length() == 1){
				buf.append("0");
				buf.append(tmp);
			}
			else{
				buf.append(tmp);
			}
		}
		
		return buf.toString();
	}
}

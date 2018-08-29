/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.jp.sde4sd.commons.db.sqlite;

import java.security.NoSuchAlgorithmException;

import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.nec.jp.sde4sd.commons.db.SDEDatabaseException;
import com.nec.jp.sde4sd.commons.db.SDEDatabaseInterface;

/**
 * 暗号化データベース操作用クラス。
 */
public class CipherSQLDatabase implements SDEDatabaseInterface {
	private static final String TAG = "CipherSQLDatabase";
	private boolean DEBUG = false;
	
	private String mKey;
	private KeyGenerator mGenerator;
	private SQLiteDatabase mDatabase;
	private CipherSQLOpenHelper mHelper;
	
	/**
	 * 暗号化DBオブジェクト作成。
	 * 
	 * @param context 暗号化DB作成用コンテキスト
	 * @param password パスワード文字列。null または空文字列を指定した場合は、暗号化を行わない。
	 * @throws CipherSQLException
	 */
	public CipherSQLDatabase(Context context, String password){
		if (DEBUG) Log.d(TAG, "CipherSQLDatabase#init");

		//暗号化キー作成
		createKeyGenerator();
		mKey = generateKey(password);

		//SQLCipher初期化
		SQLiteDatabase.loadLibs(context);
		mHelper = new CipherSQLOpenHelper(context);
	}

	private void createKeyGenerator() {
		try {
			mGenerator = KeyGenerator.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			CipherSQLException cipherEx = new CipherSQLException(e.getMessage());
			cipherEx.initCause(e);
			throw cipherEx;
		}
	}

	private String generateKey(String password) {
		if (password == null || password.isEmpty()) {
			// 暗号化なし
			return "";
		} else {
			return mGenerator.generateKey(password);
		}
	}

	/**
	 * データベースのオープン。
	 */
	@Override
	public void open() {
		if(DEBUG){
			Log.d(TAG, TAG + "CipherSQLDatabase#open");
			Log.d(TAG, "key = " + mKey);
		}
		
		try{
			mDatabase = mHelper.getWritableDatabase(mKey);
		}
		catch(SQLException e){
			SDEDatabaseException sdeEx = new SDEDatabaseException(e.getMessage());
			sdeEx.initCause(e);
			throw sdeEx;
		}		
	}

	/**
	 * データベースのクローズ。
	 */
	@Override
	public void close() {
		if(mDatabase == null){
			throw new IllegalStateException("database not open");
		}
		
		mDatabase.close();
		mDatabase = null;
	}

	/**
	 * データベースのテーブル作成。
	 * @param sql テーブル作成用SQL文
	 */
	@Override
	public void createTable(String sql) {
		if(mDatabase == null){
			throw new IllegalStateException("database not open");
		}
		
		if(sql == null){
			throw new NullPointerException("sql is null");
		}

		if(!(sql.startsWith("create table")) && !(sql.startsWith("CREATE TABLE"))){
			throw new SDEDatabaseException("invalid sql");
		}

		try{
			mDatabase.execSQL(sql);
		}
		catch(SQLException e){
			SDEDatabaseException sdeEx = new SDEDatabaseException(e.getMessage());
			sdeEx.initCause(e);
			throw sdeEx;
		}
	}

	/**
	 * データ取得。
	 * @param table テーブル名
	 * @param columns カラム
	 * @param where WHERE
	 * @param whereArgs WHERE引数
	 * @param orderBy ORDER BY
	 * @param offset OFFSET
	 * @param limit LIMIT
	 * @return Cursorオブジェクト
	 */
	@Override
	public Cursor select(String table, String[] columns, String where, String[] whereArgs, String orderBy, int offset, int limit) {
		if(mDatabase == null){
			throw new IllegalStateException("database not open");
		}
		
		String limitState = null;
		if (offset < 0) offset = 0;
		if (limit > 0) {
			limitState = offset + ", " + limit;
		}

		Cursor c = null;

		try{
			//mDatabase#queryは通常net.sqlcipher.Cursorを返すが、interfaceの都合上android.database.Cursorを返す
			c = mDatabase.query(table, columns, where, whereArgs, null, null, orderBy, limitState);
		}
		catch(SQLException e){
			SDEDatabaseException sdeEx = new SDEDatabaseException(e.getMessage());
			sdeEx.initCause(e);
			throw sdeEx;
		}
		
		return c;
	}

	/**
	 * Raw SQL クエリを発行する
	 * @param sql SQL文
	 * @param whereArgs プレースホルダに代入する引数
	 * @return Cursorオブジェクト
	 */
	@Override
	public Cursor rawQuery(String sql, String[] whereArgs) {
		if (mDatabase == null) {
			throw new IllegalStateException("database not open");
		}

		try {
			return mDatabase.rawQuery(sql, whereArgs);
		} catch (SQLException e) {
			SDEDatabaseException sdeEx = new SDEDatabaseException(e.getMessage());
			sdeEx.initCause(e);
			throw sdeEx;
		}
	}

	/**
	 * データ挿入。
	 * @param table テーブル名
	 * @param values 更新データ
	 * @return 行ID。失敗時は-1が返る。
	 */
	@Override
	public long insert(String table, ContentValues values) {
		if(mDatabase == null){
			throw new IllegalStateException("database not open");
		}

		//insertは例外を投げない
		return mDatabase.insert(table, null, values);
	}

	/**
	 * データ更新。
	 * @param table テーブル名
	 * @param values 更新データ
	 * @param where WHERE
	 * @param whereArgs
	 * @return 変更した行の数。
	 */
	@Override
	public int update(String table, ContentValues values, String where, String[] whereArgs) {
		if(mDatabase == null){
			throw new IllegalStateException("database not open");
		}
		
		int rows = 0;
		try{
			rows = mDatabase.update(table, values, where, whereArgs);
		}
		catch(SQLException e){
			SDEDatabaseException sdeEx = new SDEDatabaseException(e.getMessage());
			sdeEx.initCause(e);
			throw sdeEx;
		}

		return rows;
	}

	/**
	 * データ削除。
	 * @param table テーブル名
	 * @param where WHERE
	 * @param whereArgs
	 * @return データを削除した行の数。
	 */
	@Override
	public int delete(String table, String where, String[] whereArgs) {
		if(mDatabase == null){
			throw new IllegalStateException("database not open");
		}
		
		int rows = 0;
		try{
			rows = mDatabase.delete(table, where, whereArgs);
		}
		catch(SQLException e){
			SDEDatabaseException sdeEx = new SDEDatabaseException(e.getMessage());
			sdeEx.initCause(e);
			throw sdeEx;
		}

		return rows;
	}

	/**
	 * トランザクション開始。
	 */
	@Override
	public void begin() {
		if(mDatabase == null){
			throw new IllegalStateException("database not open");
		}

		mDatabase.beginTransaction();
	}

	/**
	 * トランザクション終了。
	 */	
	@Override
	public void commit() {
		if(mDatabase == null){
			throw new IllegalStateException("database not open");
		}

		try{
			mDatabase.setTransactionSuccessful();
		}
		finally{
			mDatabase.endTransaction();
		}
	}

	/**
	 * ロールバック。
	 */
	@Override
	public void rollback() {
		if(mDatabase == null){
			throw new IllegalStateException("database not open");
		}

		try{
			mDatabase.endTransaction();
		}
		catch(SQLException e){
			SDEDatabaseException sdeEx = new SDEDatabaseException(e.getMessage());
			sdeEx.initCause(e);
			throw sdeEx;
		}
	}

	/**
	 * SQL実行。
	 * @param sql SQL文
	 */
	@Override
	public void execSQL(String sql) {
		if(mDatabase == null){
			throw new IllegalStateException("database not open");
		}
		
		if(sql == null){
			throw new NullPointerException("sql is null");
		}

		try{
			mDatabase.execSQL(sql);
		}
		catch(SQLException e){
			SDEDatabaseException sdeEx = new SDEDatabaseException(e.getMessage());
			sdeEx.initCause(e);
			throw sdeEx;
		}
	}

	/**
	 * パスワードの変更。
	 * 
	 * @param oldPass 古いパスワード文字列
	 * @param newPass 新しいパスワード文字列
	 */
	public void changePassword(String oldPass, String newPass){
		if((oldPass == null) || (newPass == null)){
			throw new NullPointerException("password is null");			
		}
		else if(oldPass.equals("") || newPass.equals("")){
			throw new IllegalArgumentException("password is empty");
		}
		
		//mDatabaseをnullに戻すかどうかのフラグ
		boolean clearFlag = false;
		
		//現在のパスワードが正しいか比較
		String key = generateKey(oldPass);
		if(!(key.equals(mKey))){
			throw new CipherSQLException("password is not correct");
		}
		
		if(mDatabase == null){
			clearFlag = true;
			mDatabase = mHelper.getReadableDatabase(mKey);
		}
		
		//新しいキーを生成
		String newKey = generateKey(newPass);

		if(DEBUG){
			Log.d(TAG, key);
			Log.d(TAG, newKey);
		}

		//パスワード変更処理
		execSQL("PRAGMA key = " + "\"" + key + "\"");
		execSQL("PRAGMA rekey = " + "\"" + newKey + "\"");

		//新しいキーを設定
		mKey = newKey;
		
		//元々nullの場合はnullに戻す。
		if(clearFlag){
			close();
			mDatabase = null;
		}
	}

	/**
	 * データベースファイルを強制削除する
	 * @param context
     */
	public static void forceDeleteDatabase(Context context) {
		CipherSQLOpenHelper.forceDeleteDatabase(context);
	}
}

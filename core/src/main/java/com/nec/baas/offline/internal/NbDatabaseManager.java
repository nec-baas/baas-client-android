/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

import com.nec.baas.core.*;
import com.nec.baas.json.*;
import com.nec.baas.object.*;
import com.nec.baas.offline.*;
import com.nec.baas.util.*;

//import java.text.DateFormat;
//import java.text.ParseException;
//import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * DBアクセスクラス。
 * @since 1.0
 */
@Accessors(prefix ="m")
public abstract class NbDatabaseManager implements NbDatabaseWrapper {
    private static final NbLogger log = NbLogger.getLogger(NbDatabaseManager.class);

    static final int KEY_USERID   = 1;
    static final int KEY_USERNAME = 2;
    static final int KEY_EMAIL    = 3;

    // バケット管理カラム
    private static final String BUCKET_NAME_COLUMN = "bucketName";
    protected static final String DOCUMENT_COLUMN = "document";
    private static final String ACL_COLUMN = "acl";
    private static final String CONTENT_ACL_COLUMN = "contentAcl";
    private static final String POLICY_COLUMN = "policy";
    private static final String BUCKET_MODE_COLUMN = "bucketMode";

    // オブジェクトカラム
    private static final String OBJECT_ID_COLUMN = "objectId";
    protected static final String STATE_COLUMN = "state";
    private static final String TIMESTAMP_COLUMN = "timestamp";
    private static final String ETAG_COLUMN = "ETag";
    private static final String PERMISSION_COLUMN = "permission";

    // ログインキャッシュカラム
    private static final String USERID_COLUMN = "userId";
    private static final String USER_NAME_COLUMN = "userName";
    private static final String EMAIL_COLUMN = "mailAddress";
    private static final String OPTIONS_COLUMN = "options";
    private static final String PASSWORD_HASH_COLUMN = "passwordHash";
    private static final String GROUPLIST_COLUMN = "groupList";
    private static final String SESSION_COLUMN = "sessionToken";
    private static final String CREATEDAT_COLUMN = "createdAt";
    private static final String UPDATEDAT_COLUMN = "updatedAt";
    private static final String LOGINCACHE_EXPIRE_COLUMN = "loginCacheExpire";
    private static final String SESSION_EXPIRE_COLUMN = "SessionTokenExpire";

    // 同期条件カラム
    private static final String QUERY_CONDITIONS_COLUMN = "queryConditions";
    private static final String QUERY_LIMIT_COLUMN = "queryLimits";
    private static final String QUERY_SKIPCOUNT_COLUMN = "querySkipCount";
    private static final String QUERY_COUNT_COLUMN = "queryCount";
    private static final String QUERY_SORTORDER_COLUMN = "querySortOrder";
    private static final String QUERY_DELETEMARK_COLUMN = "queryDeleteMark";
    private static final String TIMESTAMP_PULL_SERVER_TIME_COLUMN = "timestampPullServerTime";

    // ファイルメタデータカラム
    private static final String FILENAME_COLUMN = "fileName";
    private static final String META_STATE_COLUMN = "metaState";
    private static final String FILE_STATE_COLUMN = "fileState";
    private static final String CREATE_TIME_COLUMN = "createTime";
    private static final String UPDATE_TIME_COLUMN = "updateTime";
    private static final String META_ETAG_COLUMN = "metaETag";
    private static final String FILE_ETAG_COLUMN = "fileETag";
    private static final String CACHE_DISABLE_COLUMN = "cacheDisable";
    private static final String PUBLIC_URL_COLUMN = "publicUrl";
    /*PERMISSION_COLUMNは既存定義*/
    private static final String FILESIZE_COLUMN = "fileSize";
    private static final String CONTENT_TYPE_COLUMN = "contentType";
    private static final String META_ID_COLUMN = "metaId";
    private static final String ID_COLUMN = "_id";

//    // 同期管理カラム
//    private static final String INTERVAL_COLUMN = "interval";

    /** オブジェクトバケット管理テーブル名 */
    private static final String BUCKET_MANAGE_TABLE = "bucketManage";
    /** ファイルバケット管理テーブル名 */
    private static final String FILE_BUCKET_MANAGE_TABLE = "fileBucketManage";
    /** ログインキャッシュテーブル名 */
    private static final String LOGINCACHE_TABLE = "loginCache";
    /** 同期状態テーブル名 */
    private static final String SYNC_CONDITION_TABLE = "syncCondition";
//    /** 同期管理テーブル名 */
//    private static final String SYNC_MANAGE_TABLE = "syncManage";

    private static final String CREATE_TABLE_SQL = "CREATE TABLE ";
    private static final String CREATE_INDEX_SQL = "CREATE INDEX ";
    private static final String ALTER_TABLE_SQL = "ALTER TABLE ";
    private static final String DROP_TABLE_SQL = "DROP TABLE ";
    private static final String WHERE_SQL = " = ?;";

    private static final String READ_DIRTY_SQL =
            STATE_COLUMN + " IN(?,?,?,?,?,?)";

    private static final String[] READ_DIRTY_ARGS = new String[]{
            NbSyncState.DIRTY.idString,
            NbSyncState.DIRTY_FULL.idString,
            NbSyncState.CONFLICTED.idString,
            NbSyncState.CONFLICTED_FULL.idString,
            NbSyncState.CONFLICTED_DELETE.idString,
            NbSyncState.DELETE.idString
    };

    // バケット命名規則
    /** オブジェクトテーブルプレフィクス */
    private static final String OBJECT_BUCKET_TBL_PREFIX = "OBJECT_";
    /** ファイルテーブルプレフィクス */
    private static final String FILE_BUCKET_TBL_PREFIX = "FILE_";


    private static final String TYPE_PRIMARY_KEY = "INTEGER PRIMARY KEY AUTOINCREMENT";
    private static final String TEXT = "TEXT";
    private static final String TEXT_UNIQUE = "TEXT UNIQUE";
//    private static final String INTEGER = "INTEGER";
    private static final String REAL = "REAL";

//    private static final String ON_CONFLICT_REPLACE = " ON CONFLICT REPLACE";

    /**
     * オブジェクトテーブルカラム定義。
     * <p>バケット一個ごとに作成され、オブジェクトテーブル名は、"OBJECT_バケット名" となる。
     * <p>注: Androidでは"_id"が存在する前提で作成されたクラスが存在するため"_id"カラムを作成する。
     */
    private static final LinkedHashMap<String,String> OBJECT_TABLE_COLUMNS_DEF
            = new LinkedHashMap<String,String>() {
        {
            put(ID_COLUMN, TYPE_PRIMARY_KEY);
            put(OBJECT_ID_COLUMN, TEXT_UNIQUE);
            put(STATE_COLUMN, TEXT);
            put(DOCUMENT_COLUMN, TEXT);
            put(TIMESTAMP_COLUMN, TEXT);
            put(ETAG_COLUMN, TEXT);
            put(PERMISSION_COLUMN,TEXT);
        }
    };

    /**
     * オブジェクトテーブルのカラムリスト
     */
    private static final String[] OBJECT_TABLE_COLUMNS =
            OBJECT_TABLE_COLUMNS_DEF.keySet().toArray(new String[1]);

    /**
     * ファイルテーブルカラム定義。
     *
     * <p>本テーブルにはメタデータが格納される。
     * バケット１個ごとに作成され、ファイルテーブル名は、"FILE_バケット名" となる。
     */
    private static final LinkedHashMap<String,String> FILE_METADATA_TABLE_COLUMNS_DEF
            = new LinkedHashMap<String,String>() {
        {
            put(ID_COLUMN, TYPE_PRIMARY_KEY);
            put(FILENAME_COLUMN, TEXT_UNIQUE);
            put(META_STATE_COLUMN, TEXT);
            put(FILE_STATE_COLUMN, TEXT);
            put(CREATE_TIME_COLUMN, TEXT);
            put(UPDATE_TIME_COLUMN, TEXT);
            put(META_ETAG_COLUMN, TEXT);
            put(FILE_ETAG_COLUMN, TEXT);
            put(CACHE_DISABLE_COLUMN, TEXT);
            put(PUBLIC_URL_COLUMN, TEXT);
            put(PERMISSION_COLUMN, TEXT);
            put(FILESIZE_COLUMN, TEXT);
            put(CONTENT_TYPE_COLUMN, TEXT);
            put(META_ID_COLUMN, TEXT);
        }
    };

    /** ファイルテーブルのカラムリスト */
    private static final String[] FILE_METADATA_TABLE_COLUMNS =
            FILE_METADATA_TABLE_COLUMNS_DEF.keySet().toArray(new String[1]);

    /** オブジェクト・ファイルバケット管理テーブルのカラム定義 (共通) */
    private static final LinkedHashMap<String,String> BUCKET_MANAGE_TABLE_COLUMNS_DEF
            = new LinkedHashMap<String, String>() {
        {
            put(ID_COLUMN, TYPE_PRIMARY_KEY);
            put(BUCKET_NAME_COLUMN, TEXT_UNIQUE);
            put(DOCUMENT_COLUMN, TEXT);
            put(ACL_COLUMN, TEXT);
            put(CONTENT_ACL_COLUMN, TEXT);
            put(POLICY_COLUMN, TEXT);
            put(BUCKET_MODE_COLUMN, TEXT);
        }
    };

    /** オブジェクト・ファイルバケット管理テーブルのカラムリスト */
    private static final String[] BUCKET_MANAGE_COLUMNS =
            BUCKET_MANAGE_TABLE_COLUMNS_DEF.keySet().toArray(new String[1]);

    /** ログインキャッシュテーブル(loginCache)のカラム定義 */
    protected static final LinkedHashMap<String,String> LOGINCACHE_TABLE_COLUMNS_DEF
            = new LinkedHashMap<String,String>() {
        {
            put(ID_COLUMN, TYPE_PRIMARY_KEY);
            put(USERID_COLUMN, TEXT_UNIQUE);
            put(USER_NAME_COLUMN, TEXT);
            put(EMAIL_COLUMN, TEXT);
            put(OPTIONS_COLUMN, TEXT);
            put(PASSWORD_HASH_COLUMN, TEXT);
            put(GROUPLIST_COLUMN, TEXT);
            put(CREATEDAT_COLUMN, TEXT);
            put(UPDATEDAT_COLUMN, TEXT);
            put(LOGINCACHE_EXPIRE_COLUMN, TEXT);
            put(SESSION_COLUMN, TEXT);
            put(SESSION_EXPIRE_COLUMN, TEXT);
        }
    };

    /** ログインキャッシュテーブルのカラムリスト */
    private static final String[] LOGINCACHE_TABLE_COLUMNS =
            LOGINCACHE_TABLE_COLUMNS_DEF.keySet().toArray(new String[1]);

    /** 同期条件テーブル(syncCondition)のカラム定義 */
    protected static final LinkedHashMap<String,String> SYNC_CONDITION_TABLE_COLUMNS_DEF
            = new LinkedHashMap<String,String>() {
        {
            put(ID_COLUMN, TYPE_PRIMARY_KEY);
            put(BUCKET_NAME_COLUMN, TEXT_UNIQUE);
            put(QUERY_CONDITIONS_COLUMN, TEXT);
            put(QUERY_LIMIT_COLUMN, TEXT);
            put(QUERY_SKIPCOUNT_COLUMN, TEXT);
            put(QUERY_COUNT_COLUMN, TEXT);
            put(QUERY_SORTORDER_COLUMN, TEXT);
            put(QUERY_DELETEMARK_COLUMN, TEXT);
            put(TIMESTAMP_COLUMN, TEXT);
            put(TIMESTAMP_PULL_SERVER_TIME_COLUMN, TEXT);
        }
    };

//    /** 同期管理テーブル(syncManage)のカラム定義 */
//    protected static final LinkedHashMap<String,String> SYNC_MANAGE_TABLE_COLUMNS_DEF
//            = new LinkedHashMap<String,String>() {
//        {
//            put(ID_COLUMN, TYPE_PRIMARY_KEY);
//            put(BUCKET_NAME_COLUMN, TEXT_UNIQUE + ON_CONFLICT_REPLACE);
//            put(INTERVAL_COLUMN, INTEGER);
//        }
//    };
//
//    private static final String[] SYNC_MANAGE_TABLE_COLUMNS =
//            SYNC_MANAGE_TABLE_COLUMNS_DEF.keySet().toArray(new String[1]);

    static final long INSERT_ERROR_CODE = -1;
    //private final static int QUERY_ALL_GET = -1;

    /**
     * 同期範囲データのキャッシュ
     */
    @Setter // for test
    private Map<String, NbQuery> mSyncScopeCache = null;

    private NbDatabaseQueryStrategy mDatabaseQueryStrategy;

    private DatabaseHook mDataSecurityHook = null;

    /**
     * デフォルトコンストラクタ
     */
    protected NbDatabaseManager() {
        // initialize() 呼び出しは、各サブクラスのコンストラクタで行う。

        if (NbConsts.ENABLE_DATA_SECURITY) {
            mDataSecurityHook = new DatabaseSecurityHook();
        }
    }

    /**
     * 初期化処理
     */
    protected void initialize() {
        open();
        //auto_vacuumを有効に設定
        setAutoVacuum();
        createTables();

        mDatabaseQueryStrategy = new NbDatabaseQueryStrategyImpl(this);
    }

    private void setAutoVacuum(){
        //テーブル存在確認
        CursorWrapper c = selectForCursor(
                "sqlite_master",
                new String[]{"COUNT(*)"},
                "type=? AND name=?",
                new String[]{"table", BUCKET_MANAGE_TABLE},
                null,
                0,
                0
        );
        c.moveToFirst();
        //バケット管理テーブルがない場合のみauto_vacuumを設定する
        if(c.getString(0).equals("0")){
            log.fine("set auto_vacuum");
            execSQL("PRAGMA auto_vacuum=1");
            execSQL("VACUUM");
        }
        c.close();
    }

    private void createTables() {
        //バケット管理テーブル作成
        tryCreateTable(BUCKET_MANAGE_TABLE, BUCKET_MANAGE_TABLE_COLUMNS_DEF);

        //ファイルバケット管理テーブル作成
        tryCreateTable(FILE_BUCKET_MANAGE_TABLE, BUCKET_MANAGE_TABLE_COLUMNS_DEF);

        //ログインキャッシュテーブル作成
        tryCreateTable(LOGINCACHE_TABLE, LOGINCACHE_TABLE_COLUMNS_DEF);

        //同期条件テーブル作成（同期範囲、同期対象）
        tryCreateTable(SYNC_CONDITION_TABLE, SYNC_CONDITION_TABLE_COLUMNS_DEF);

//        //同期管理テーブル作成（同期間隔、前回同期時刻）
//        tryCreateTable(SYNC_MANAGE_TABLE, SYNC_MANAGE_TABLE_COLUMNS_DEF);
    }

    private void tryCreateTable(String tableName, LinkedHashMap<String, String> columns) {
        try {
            createTable(tableName, columns);
        } catch (Exception e) {
            //すでにテーブルが存在する場合発生する
        }
    }

    /**
     * テーブルを作成する
     * @param tableName テーブル名
     * @param columns カラム名とカラム定義(型や制約)のマップ
     */
    protected void createTable(String tableName, LinkedHashMap<String, String> columns) {
        StringBuilder sql = new StringBuilder();
        sql.append(CREATE_TABLE_SQL);
        sql.append(tableName);
        sql.append(" (");
        boolean isFirst = true;
        for (Map.Entry<String, String> column : columns.entrySet()) {
            if (isFirst) {
                isFirst = false;
            } else {
                sql.append(", ");
            }
            sql.append(column.getKey()); // column name
            sql.append(" ");
            sql.append(column.getValue()); // column type etc.
        }
        sql.append(")");

        String sqlString = sql.toString();
        log.fine("createTable: " + sqlString);
        execSQL(sqlString);
    }

    /** JSONの型とDBの型のマッピングテーブル */
    protected static final HashMap<String,String> TYPE_JSON_TO_DB_TABLE_DEF
            = new HashMap<String,String>() {
        {
            put(NbIndexType.STRING.type(), TEXT);
            put(NbIndexType.BOOLEAN.type(), TEXT);
            put(NbIndexType.NUMBER.type(), REAL);
        }
    };

    /** Indexキー名が予約語とかぶっても問題ないようにIndexキー名にprefixをつける */
    private static final String INDEX_PREFIX = "IDX_";

    /** Indexキー名とその型を示す文字列を区切る文字 */
    // 文字列をカラムに含めておくのは、getIndexFromLocal()で型を取り出せるようにするため
    private static final String INDEX_TYPE_DELIMITER = "_";

    // NbMongoQueryConverterから呼び出したいのでstaticにする
    protected static String getIndexKeyForColumn(String key, NbIndexType type) {
        return INDEX_PREFIX + key + INDEX_TYPE_DELIMITER + type.type();
    }

    private String getIndexTypeFromLocal(String indexColumnName) {
        int index = indexColumnName.lastIndexOf(INDEX_TYPE_DELIMITER);
        index += INDEX_TYPE_DELIMITER.length();
        return indexColumnName.substring(index);
    }

    private String getIndexKeyFromLocal(String indexColumnName) {
        int index = indexColumnName.lastIndexOf(INDEX_TYPE_DELIMITER);
        return indexColumnName.substring(INDEX_PREFIX.length(), index);
    }

    public void setIndex(@NonNull final String bucketName, @NonNull Map<String, NbIndexType> indexKeys) {
        final String TEMP_PREFIX = "TEMP_";
        final String tempBucketName = TEMP_PREFIX + bucketName;

        try {
            // トランザクション開始
            begin();

            // オブジェクトテーブル新規作成(インデックスカラムを追加)
            LinkedHashMap<String, String> columnsWithIndex = new LinkedHashMap<>(OBJECT_TABLE_COLUMNS_DEF);
            HashMap<String, String> indexColumns = new HashMap<>();
            for (Map.Entry<String, NbIndexType> entry : indexKeys.entrySet()) {
                String key = getIndexKeyForColumn(entry.getKey(), entry.getValue());
                // DBの型はテーブルから引き出す
                indexColumns.put(key, TYPE_JSON_TO_DB_TABLE_DEF.get(entry.getValue().type()));
            }
            columnsWithIndex.putAll(indexColumns);
            createTable(getObjectTableName(tempBucketName), columnsWithIndex);

            // 旧テーブルからデータを取得
            NbQuery query = new NbQuery();
            query.setLimit(-1);
            ObjectQueryResults result = queryObjects(bucketName, query);

            // 新テーブルへ登録(パースしてインデックスカラムの作成も行う)
            for (int i = 0; i < result.getResults().size(); i++) {
                NbObjectEntity objectEntity = result.getResults().get(i);
                log.fine("objectEntity.getObjectId(): " + objectEntity.getObjectId());
                createObject(tempBucketName, objectEntity);
            }

            // 旧テーブルを削除(バケット管理テーブルは操作不要)
            String dropSql = DROP_TABLE_SQL + (getObjectTableName(bucketName)) + ";";
            log.fine("drop table: " + dropSql);
            execSQL(dropSql);

            // 明示的にインデックス削除SQLを実行しなくても旧テーブルのインデックスは削除される

            // 新テーブルを旧テーブルにリネーム
            StringBuilder sql = new StringBuilder();
            sql.append(ALTER_TABLE_SQL);
            sql.append(getObjectTableName(tempBucketName));
            sql.append(" RENAME TO ");
            sql.append(getObjectTableName(bucketName));
            log.fine("rename table: " + sql.toString());
            execSQL(sql.toString());

            // インデックスが設定されている場合は、CREATE INDEX文を実行する
            if (!indexKeys.isEmpty()) {
                StringBuilder indexSql = new StringBuilder();
                indexSql.append(CREATE_INDEX_SQL);
                indexSql.append(getObjectTableName(bucketName)).append("_IDX");
                indexSql.append(" ON ");
                indexSql.append(getObjectTableName(bucketName));
                indexSql.append("(");
                boolean isFirst = true;
                for (String key : indexColumns.keySet()) {
                    log.fine("key: " + key);
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        indexSql.append(", ");
                    }
                    indexSql.append(key);
                }
                indexSql.append(")");
                log.fine("create index: " + indexSql.toString());
                execSQL(indexSql.toString());
            }

            // トランザクション終了(DBへ反映)
            commit();
        } catch (Exception e) {
            log.severe("set index error: " + e);
            rollback();
            throw new NbDatabaseException(e);
        } finally {
            // DBの再オープン
            // これを実施しないと次のインデックス取得が古いままとなってしまう
            // 尚、この操作により後発のDBアクセス処理が待機していた場合、その後発処理エラーや例外となる可能性がある(制限事項)
            /*try {
                reopen();
            } catch (Exception e) {
                log.severe("set index close error: " + e);
                throw new NbDatabaseException(e);
            }*/
        }
    }

    public Map<String, NbIndexType> getIndex(@NonNull String bucketName) {
        return getIndexWithTable(getObjectTableName(bucketName));
    }

    /**
     * テーブル名を指定してインデックスキーを取得する。
     * @param table テーブル名
     * @return 指定したテーブルに設定されているインデックスキー
     */
    public Map<String, NbIndexType> getIndexWithTable(@NonNull String table) {
        //log.fine("getIndexWithTable <start> table: " + table);

        Map<String, NbIndexType> result = new HashMap<>();

        // オブジェクトテーブル以外が指定されたら、即終了する(オブジェクトテーブル以外のテーブルから呼び出された場合を考慮)
        if (!isObjectTableName(table)) {
            log.warning("not object table...: " + table);
            return result;
        }

        List<String> columnNames = getColumnNames(table);
        for (String key : columnNames) {
            //log.fine("getIndexWithTable() key=" + key);
            if (!OBJECT_TABLE_COLUMNS_DEF.containsKey(key)) {
                String indexKey = getIndexKeyFromLocal(key);
                String indexType = getIndexTypeFromLocal(key);
                result.put(indexKey, NbIndexType.fromString(indexType));
            }
        }
        //log.fine("getIndexWithTable <end>");
        return result;
    }

    /**
     * カラム名の一覧を取得する。
     * Note: SQLCipher では正しく取得できない(特に limit != 0 の場合)ので、
     * Android 実装では別実装にオーバライドで差し替える。
     * @param table
     * @return
     */
    protected List<String> getColumnNames(String table) {
        //log.fine("getColumnNames <start> table: " + table);
        List<String> list = new ArrayList<>();

        CursorWrapper cursor = null;
        try {
            cursor = selectForCursor(table, null, null, null, null, 0, 1);
            //log.fine("getColumnNames: column count: " + cursor.getColumnCount());

            // テーブルがない場合などに例外を発生させるためのダミー処理
            // (テーブルがなくてもselectForCursor()では例外を投げない)
            cursor.moveToFirst();

            for (int i = 0; i < cursor.getColumnCount(); i++) {
                //log.fine("column: " + cursor.getColumnName(i));
                list.add(cursor.getColumnName(i));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        //log.fine("getColumnNames <end>");
        return list;
    }

    //------------------------------------------------------------------------------------
    // Bucket DAO
    //------------------------------------------------------------------------------------

    /**
     * バケット情報の全件取得を行う。
     * @param isFile true - ファイルバケット、false - オブジェクトバケット
     * @return バケット情報リスト
     */
    public List<NbBucketEntity> readBucketList(boolean isFile) {
        return readBucketList(getManageTableName(isFile));
    }

    private List<NbBucketEntity> readBucketList(@NonNull String manageTableName) {
        List<Map<String, String>> result = select(manageTableName, BUCKET_MANAGE_COLUMNS,
                null, null, null, 0, 0);
        List<NbBucketEntity> dataList = new ArrayList<>();
        for (Map<String, String> resultMap : result) {
            NbBucketEntity data = makeBucketDataInfo(resultMap);
            dataList.add(data);
        }
        return dataList;
    }

    /**
     * バケット情報の取得を行う。
     * @param bucketName バケット名
     * @return バケット情報
     */
    public NbBucketEntity readBucket(String bucketName, boolean isFile) {
        return readBucket(bucketName, getManageTableName(isFile));
    }

    private NbBucketEntity readBucket(@NonNull String bucketName, @NonNull String manageTableName) {
        String where = BUCKET_NAME_COLUMN + WHERE_SQL;
        String[] whereArg = new String[]{bucketName};
        List<Map<String, String>> result = select(manageTableName, BUCKET_MANAGE_COLUMNS,
                where, whereArg, null, 0, 0);
        NbBucketEntity data = null;
        if (!result.isEmpty()) {
            Map<String, String> resultMap = result.get(0);
            data = makeBucketDataInfo(resultMap);
        }
        return data;
    }

    private NbBucketEntity makeBucketDataInfo(Map<String, String> data) {
        NbJSONObject aclJson = NbJSONParser.parse(data.get(ACL_COLUMN));
        NbAcl acl = NbOfflineUtil.makeAcl(aclJson);

        aclJson = NbJSONParser.parse(data.get(CONTENT_ACL_COLUMN));
        NbContentAcl contentAcl = NbOfflineUtil.makeContentAcl(aclJson);

        NbBucketEntity info = new NbBucketEntity();
        info.setAcl(acl);
        info.setContentAcl(contentAcl);
        info.setBucketName(data.get(BUCKET_NAME_COLUMN));
        info.setJsonData(data.get(DOCUMENT_COLUMN));
        info.setPolicy(NbConflictResolvePolicy.fromObject(data.get(POLICY_COLUMN)));
        info.setBucketMode(NbBucketMode.fromObject(data.get(BUCKET_MODE_COLUMN)));
        return info;
    }

    /**
     * バケットの作成を行う。<br>
     * ユーザテーブルの作成も行う。
     * @param bucketName    バケット名
     * @param data          バケット情報
     * @param isFile        ファイルストレージか否か
     * @return 作成したバケット情報のid
     */
    public long createBucket(String bucketName, NbBucketEntity data, boolean isFile) {
        return createBucket(bucketName, getBucketTablePrefixName(isFile),
                getManageTableName(isFile), getTableColumnsDef(isFile), data);
    }

    private long createBucket(@NonNull String bucketName, @NonNull String tablePrefix, @NonNull String manageTableName,
                              @NonNull LinkedHashMap<String, String> tableColumns, @NonNull NbBucketEntity data) {
        log.finest("createBucket() <start> bucketName={0}", bucketName);
        bucketDataSanityCheck(data);

        // テーブル作成
        try {
            createTable(tablePrefix + bucketName, tableColumns);
        } catch (Exception e) {
            //テーブルが重複している場合発生
        }

        // バケット管理テーブルにエントリを追加
        long result = insert(manageTableName, makeBucketManageTuple(bucketName, data));

        // インデックス取得をキャッシュから読み出さないようにするため、
        // バケットの増減が発生した場合はDBをオープンし直す
        //reopen();

        log.finest("createBucket() <end>");
        return result;
    }

    private Map<String,String> makeBucketManageTuple(String bucketName, NbBucketEntity data) {
        Map<String, String> tuple = new HashMap<>();
        tuple.put(BUCKET_NAME_COLUMN, bucketName);
        tuple.put(DOCUMENT_COLUMN, data.getJsonData());
        tuple.put(ACL_COLUMN, data.getAcl().toJsonString());
        tuple.put(CONTENT_ACL_COLUMN, data.getContentAcl().toJsonString());
        tuple.put(POLICY_COLUMN, data.getPolicy().idString());
        tuple.put(BUCKET_MODE_COLUMN, data.getBucketMode().idString());
        return tuple;
    }

    private void bucketDataSanityCheck(NbBucketEntity data) {
        /*
        NbJSONObject document = NbJSONParser.parse(data.getJsonString());
        if (document == null || !document.containsKey("name")) {
            throw new IllegalArgumentException("Bad JSON");
        }
        */
    }

    /**
     * バケット情報の更新を行う。
     * @param bucketName バケット名
     * @param data バケット情報
     * @param isFile true - ファイルバケット, false - オブジェクトバケット
     * @return 更新した行の数
     */
    public int updateBucket(String bucketName, NbBucketEntity data, boolean isFile) {
        return updateBucket(bucketName, data, getManageTableName(isFile));
    }

    private int updateBucket(@NonNull String bucketName, @NonNull NbBucketEntity data, @NonNull String manageTableName) {
        if (data.getBucketName() == null) {
            throw new IllegalArgumentException("No bucket name");
        }
        bucketDataSanityCheck(data);

        Map<String, String> updateData = makeBucketManageTuple(data.getBucketName(), data);

        String where = BUCKET_NAME_COLUMN + WHERE_SQL;
        String[] whereArg = new String[]{bucketName};
        int result = update(manageTableName, updateData, where, whereArg);
        return result;
    }

    /**
     * バケット情報の削除を行う。<br>
     * ユーザテーブルの削除も行う。
     * @param bucketName バケット名
     * @param isFile true - ファイルバケット, false - オブジェクトバケット
     * @return 削除した行の数
     */
    public int deleteBucket(String bucketName, boolean isFile) {
        return deleteBucket(bucketName, getBucketTablePrefixName(isFile),
                getManageTableName(isFile));
    }

    private int deleteBucket(@NonNull String bucketName, @NonNull String tablePrefix, @NonNull String manageTableName) {
        String where = BUCKET_NAME_COLUMN + WHERE_SQL;
        String[] whereArg = new String[]{bucketName};
        int result = delete(manageTableName, where, whereArg);

        String sql = DROP_TABLE_SQL + (tablePrefix + bucketName) + ";";
        execSQL(sql);

        //DataSecurity
        if (mDataSecurityHook != null) {
            mDataSecurityHook.deleteBucket(bucketName);
        }

        // インデックス取得をキャッシュから読み出さないようにするため、
        // バケットの増減が発生した場合はDBをオープンし直す
        //reopen();

        return result;
    }

    /**
     * DBに同じバケット名、バケットモードのバケットが存在するかを確認する。<br>
     *
     * @param bucketName バケット名
     * @param bucketMode バケットモード
     * @param isFile true - ファイルバケット, false - オブジェクトバケット
     * @return ローカルDBに同じバケット名、バケットモードのバケットが存在する場合はtrueを返却する
     */
    public boolean isBucketExists(String bucketName, NbBucketMode bucketMode, boolean isFile) {
        return isBucketExists(bucketName, bucketMode, getManageTableName(isFile));
    }

    private boolean isBucketExists(@NonNull String bucketName, @NonNull NbBucketMode bucketMode, @NonNull String manageTableName) {

        String where = BUCKET_NAME_COLUMN + " = ?" + " AND " + BUCKET_MODE_COLUMN + WHERE_SQL;
        String[] whereArg = new String[]{bucketName, bucketMode.idString()};
        // 存在確認なので、limitは1にする
        List<Map<String, String>> result = select(manageTableName, BUCKET_MANAGE_COLUMNS, where, whereArg, null, 0, 1);

        return !result.isEmpty();
    }

    /**
     * バケット内にデータが存在するかを確認する。<br>
     *
     * @param bucketName バケット名
     * @param isFile true - ファイルバケット, false - オブジェクトバケット
     * @return 存在する場合はtrueを返却する
     */
    public boolean isDataExistsInBucket(String bucketName, boolean isFile) {
        return isDataExistsInBucket(bucketName, getBucketTablePrefixName(isFile), getTableColumns(isFile));
    }

    private boolean isDataExistsInBucket(@NonNull String bucketName, @NonNull String tablePrefix, @NonNull String[] columns) {

        String where = ID_COLUMN + " IS NOT NULL";

        // オブジェクト/ファイルテーブルにデータが存在するか(1件でもヒットしたら、データがあることになるので、limitは1)
        List<Map<String, String>> result = select(tablePrefix + bucketName, columns, where, null, null, 0, 1);

        return !result.isEmpty();
    }

    //------------------------------------------------------------------------------------
    // Object DAO
    //------------------------------------------------------------------------------------

    /**
     * オブジェクトデータの作成(INSERT)を行う。
     * オブジェクトID は data にセットしておくこと。
     * @param bucketName バケット名
     * @param data データ
     * @return 作成したデータのId
     */
    public long createObject(String bucketName, NbObjectEntity data) {
        return createObject(data.getObjectId(), bucketName, data);
    }

    /**
     * オブジェクトデータの作成(INSERT)を行う。
     * @param objectId Object ID
     * @param bucketName バケット名
     * @param data データ
     * @return 作成したデータのId
     */
    public long createObject(@NonNull String objectId, @NonNull String bucketName, @NonNull NbObjectEntity data) {
        log.finest("createObject() objectId={0} bucketName={1}", objectId, bucketName);

        Map<String, String> createData = makeObjectTuple(objectId, data);

        long result = insert(getObjectTableName(bucketName), createData);

        //DataSecurity
        if (mDataSecurityHook != null) {
            mDataSecurityHook.createObject(bucketName, objectId, data);
        }
        return result;
    }

    /**
     * Objcet ID を指定してオブジェクトデータの読み込みを行う。
     * @param objectId オブジェクトID
     * @param bucketName バケット名
     * @return 読み込んだオブジェクトデータ。
     */
    public NbObjectEntity readObject(@NonNull String objectId, @NonNull String bucketName) {

        String where = OBJECT_ID_COLUMN + WHERE_SQL;
        String[] whereArg = new String[]{objectId};
        List<Map<String, String>> result = select(getObjectTableName(bucketName),
                OBJECT_TABLE_COLUMNS, where, whereArg, null, 0, 0);
        NbObjectEntity data = null;
        if (!result.isEmpty()) {
            Map<String, String> resultMap = result.get(0);
            data = makeObjectDataInfo(resultMap);
        }
        return data;
    }

    /**
     * オブジェクトデータの更新を行う。
     *
     * <p>オブジェクトIDは変更されない。
     *
     * @param bucketName バケット名
     * @param data 更新情報
     * @return 更新を行った行の数
     */
    public int updateObject(String bucketName, NbObjectEntity data) {
        return updateObject(data.getObjectId(), bucketName, data);
    }

    /**
     * オブジェクトデータの更新を行う。
     *
     * <p>objectId と data.objectId が異なる場合、オブジェクトIDが変更される。
     *
     * @param objectId オブジェクトID(検索用)
     * @param bucketName バケット名
     * @param data 更新情報
     * @return 更新を行った行の数
     */
    public int updateObject(@NonNull String objectId, @NonNull String bucketName, @NonNull NbObjectEntity data) {
        Map<String, String> updateData = makeObjectTuple(data.getObjectId(), data);

        String where = OBJECT_ID_COLUMN + WHERE_SQL;
        String[] whereArg = new String[]{objectId};
        int result = update(getObjectTableName(bucketName), updateData, where, whereArg);

        //DataSecurity
        if (mDataSecurityHook != null) {
            mDataSecurityHook.updateObject(bucketName, objectId, data);
        }
        return result;
    }

    /**
     * オブジェクトデータの削除を行う
     * @param objectId オブジェクトID
     * @param bucketName バケット名
     * @return 削除した行の数
     */
    public int deleteObject(@NonNull String objectId, @NonNull String bucketName) {
        log.finest("deleteObject() objectId={0} bucketName={1}", objectId, bucketName);

        //DataSecurity
        NbObjectEntity data = null;
        if (mDataSecurityHook != null) {
            data = readObject(objectId, bucketName);
        }

        String where = OBJECT_ID_COLUMN + WHERE_SQL;
        String[] whereArg = new String[]{objectId};
        int result = delete(getObjectTableName(bucketName), where, whereArg);

        //DataSecurity
        if (mDataSecurityHook != null) {
            mDataSecurityHook.deleteObject(bucketName, objectId, data);
        }
        return result;
    }

    /**
     * クエリ結果格納クラス
     */
    @Getter
    @Accessors(prefix = "")
    public static class ObjectQueryResults {
        /** クエリ結果 */
        private List<NbObjectEntity> results = new ArrayList<>();
        /** クエリ総件数。limit 指定した場合 results 件数より多い場合がある。 */
        private int totalCount;
    }

    /**
     * オブジェクトデータのクエリを行う。
     * @param bucketName バケット名
     * @param query クエリ
     * @return クエリ結果
     */
    public ObjectQueryResults queryObjects(String bucketName, NbQuery query) {
        return queryObjects(bucketName, query, null, null);
    }

    /**
     * オブジェクトデータの MongoDB ライクなクエリを行う。
     * @param bucketName バケット名
     * @param query クエリ
     * @param where SQL SELECT の where 節
     * @param whereArgs where パラメータ
     * @return クエリ結果
     */
    public ObjectQueryResults queryObjects(@NonNull String bucketName, NbQuery query, String where, String[] whereArgs) {
        log.finest("queryObjects() <start> bucketName={0} query={1}", bucketName, query);

        NbSelectObjectResults selectResults = mDatabaseQueryStrategy.select(getObjectTableName(bucketName),
                OBJECT_TABLE_COLUMNS, where, whereArgs, query);

        ObjectQueryResults queryResults = new ObjectQueryResults();
        for (NbSelectObjectResults.Result selectObjectResult : selectResults.getResults()) {
            NbObjectEntity data = makeObjectDataInfo(selectObjectResult.getColumns(), selectObjectResult.getJson());
            queryResults.results.add(data);
        }
        queryResults.totalCount = selectResults.getTotalCount();

        log.finest("queryObjects() <end> resultList={0}", queryResults.results);
        return queryResults;
    }

    /**
     * ダーティなデータを取得する。
     * @param bucketName バケット名
     * @param extConditions 対象を絞り込む条件
     * @return ダーティデータのリスト
     */
    public List<NbObjectEntity> readDirtyObjects(@NonNull String bucketName, NbWhere extConditions) {
        log.finest("readDirtyObjects() <start> bucketName={0}", bucketName);

        List<NbObjectEntity> resultList = new ArrayList<>();
        List<Map<String, String>> result;

        String conditions = READ_DIRTY_SQL;
        List<String> args = new ArrayList<>(Arrays.asList(READ_DIRTY_ARGS));

        // 追加条件があるならANDで連結
        if (extConditions != null){
            conditions += " AND " + extConditions.getWhere().toString();
            args.addAll(extConditions.getWhereArgs());
        }

        try {
            result = select(getObjectTableName(bucketName), OBJECT_TABLE_COLUMNS,
                    conditions, args.toArray(new String[args.size()]), null, 0, 0);
        } catch (NbDatabaseException e) {
            log.severe("readDirtyObjects() db select error. " + e.getMessage());
            return resultList;
        }

        for (Map<String, String> resultMap : result) {
            NbObjectEntity data = makeObjectDataInfo(resultMap);

            log.finest("readDirtyObjects() objectId={0} state={1}",
                    data.getObjectId(), data.getState());
            resultList.add(data);
        }
        log.finest("readDirtyObjects() <end> resultList={0}", resultList);

        return resultList;
    }

    /**
     * ダーティなデータのオブジェクトIDを取得する。
     * @param bucketName バケット名
     * @return ダーティデータのリスト
     */
    public String[] readDirtyObjectIds(@NonNull String bucketName) {
        log.finest("readDirtyObjectIds() <start> bucketName={0}", bucketName);

        List<String> resultList = new ArrayList<>();
        List<Map<String, String>> result;
        String[] column = {OBJECT_ID_COLUMN};

        try {
            result = select(getObjectTableName(bucketName), column,
                    READ_DIRTY_SQL, READ_DIRTY_ARGS, null, 0, 0);
        } catch (NbDatabaseException e) {
            log.severe("readDirtyObjectIds() db select error. " + e.getMessage());
            return resultList.toArray(new String[resultList.size()]);
        }

        for (Map<String, String> resultMap : result) {
            resultList.add(resultMap.get(OBJECT_ID_COLUMN));
        }

        log.finest("readDirtyObjectIds() <end> resultList={0}", resultList);

        return resultList.toArray(new String[resultList.size()]);
    }

    /**
     * select結果に対しMongoDBのwhere句チェックを行う。<br>
     * whereが無い場合は検索条件なしなのでtrueを返す
     *
     * @param jsonString JSONドキュメント
     * @param expr MongoDBのクエリ式(JSON)
     * @return マッチした場合は マッチしたJSON、マッチしなかった場合は null。JSON は Immutable なので注意すること。
     */
    protected NbJSONObject matchJsonWithQuery(String jsonString, NbJSONObject expr) {
        NbJSONObject document = NbJSONParser.parseWithCache(jsonString, false);
        if (document == null) {
            //変換不可
            log.finest("matchJsonWithQuery() Invalid JSON.");
            return null ;
        }

        //log.finest("matchJsonWithQuery() document={0}", document);
        //log.finest("matchJsonWithQuery() expr={0}", expr);

        if (expr != null) {
            /*
             * where句チェック
             * ・ドキュメント内にwhereで指定した条件に一致しない場合チェックリストから削除
             */
            if (!mMongoQueryEvaluator.evaluate(document, expr)) {
                return null;
            }
        }

        return document;
    }

    private static NbMongoQueryEvaluator mMongoQueryEvaluator = new NbMongoQueryEvaluator();

    /**
     * オブジェクトテーブル INSERT / UPDATE 用の値のタプル(組)を作成する
     * @param objectId オブジェクトID
     * @param data NbObjectEntity
     * @return タプル
     * @see #makeObjectDataInfo
     */
    private Map<String, String> makeObjectTuple(String objectId, NbObjectEntity data) {
        Map<String, String> tuple = new HashMap<>();
        tuple.put(OBJECT_ID_COLUMN, objectId);
        tuple.put(STATE_COLUMN, data.getState().idString);
        tuple.put(DOCUMENT_COLUMN, data.getJsonString());
        tuple.put(TIMESTAMP_COLUMN, data.getTimestamp());
        tuple.put(ETAG_COLUMN, data.getETag());
        tuple.put(PERMISSION_COLUMN, data.getAclString());
        return tuple;
    }

    /**
     * タプルから NbObjectEntity への変換
     * @param tuple タプル
     * @return NbObjectEntity
     * @see #makeObjectTuple
     */
    public NbObjectEntity makeObjectDataInfo(Map<String, String> tuple) {
        return makeObjectDataInfo(tuple, null);
    }

    /**
     * タプルから NbObjectEntity への変換
     * @param tuple タプル
     * @param jsonDocument パース済み NbJSONObject ("document" カラム相当)
     * @return NbObjectEntity
     * @see #makeObjectTuple
     */
    private NbObjectEntity makeObjectDataInfo(Map<String, String> tuple, NbJSONObject jsonDocument) {
        NbObjectEntity info = new NbObjectEntity();
        info.setObjectId(tuple.get(OBJECT_ID_COLUMN));
        if (jsonDocument == null) {
            info.setJsonString(tuple.get(DOCUMENT_COLUMN));
        } else {
            info.setJsonObjectAsImmutable(jsonDocument);
            info.setJsonStringDontClearObject(tuple.get(DOCUMENT_COLUMN));
        }
        info.setState(NbSyncState.fromObject(tuple.get(STATE_COLUMN)));
        info.setTimestamp(tuple.get(TIMESTAMP_COLUMN));
        info.setETag(tuple.get(ETAG_COLUMN));
        info.setAclString(tuple.get(PERMISSION_COLUMN));
        return info;
    }

    /**
     * オブジェクトバケット内に衝突データが存在するか調べる。
     * 対象となるのは NbSyncState.CONFLICTED または NbSyncState.CONFLICTED_DELETE のいずれか。
     * @param bucketName オブジェクトバケット
     * @return 衝突データがあれば true
     */
    public boolean isObjectConflictExists(String bucketName) {
        // SELECT で条件検索。LIMIT は 1。
        // 1件でもヒットしたら true を返す。
        String where = STATE_COLUMN
                + " IN(" + NbSyncState.CONFLICTED.idString
                + "," + NbSyncState.CONFLICTED_DELETE.idString
                + "," + NbSyncState.CONFLICTED_FULL.idString
                + ")";
        List<Map<String, String>> rows = select(getObjectTableName(bucketName), new String[] {OBJECT_ID_COLUMN},
                where, null, null, 0, 1);
        return rows.size() > 0;
    }

    /**
     * 同期済みデータから最寄りの更新時間を取得する。
     * @param bucketName
     * @return
     */
    /*
    public synchronized String readLatestSyncTimeStamp(String bucketName) {
        // SYNC 状態の行を選択
        String where = STATE_COLUMN + " = " + NbSyncState.SYNC.idString;
        String[] whereArgs = null;

        //前回時刻よりも未来のデータを対象
        String preTimeStamp = getLastSyncTime(bucketName);
        if (preTimeStamp != null) {
            where = where + " AND " + TIMESTAMP_COLUMN + " > ?";
            whereArgs = new String[] { preTimeStamp };
        }

        // select 実行。タイムスタンプ降順(新しい順)で1件のみ
        List<Map<String, String>> rows = select(getObjectTableName(bucketName),
                new String[] {TIMESTAMP_COLUMN}, where, whereArgs, TIMESTAMP_COLUMN + " DESC", 0, 1);
        if (rows.size() == 0) {
            log.finest("readLatestSyncTimeStamp() return null");
            return null; // なし
        } else {
            String latestSyncTime = rows.get(0).get(TIMESTAMP_COLUMN);
            log.finest("readLatestSyncTimeStamp() return " + latestSyncTime);
            return latestSyncTime;
        }
    }
    */

    /**
     * syncing状態のデータをsync状態に更新する。
     * @param bucketName バケット名
     * @return 更新したデータ数
     */
    public int updateSyncingObjects(String bucketName) {
        String where = STATE_COLUMN + " = " + NbSyncState.SYNCING.idString;

        Map<String, String> values = new HashMap<>();
        values.put(STATE_COLUMN, NbSyncState.SYNC.idString);

        return update(getObjectTableName(bucketName), values, where, null);
    }


    //------------------------------------------------------------------------------------
    // Login Cache DAO
    //------------------------------------------------------------------------------------

    /**
     * ログインキャッシュの全件取得を行う。
     * @return ログインキャッシュ情報
     */
    public List<NbLoginCacheEntity> readAllLoginCache() {
        log.finest("readAllLoginCache() <start>");

        List<NbLoginCacheEntity> resultList = new ArrayList<>();
        List<Map<String, String>> result = select(LOGINCACHE_TABLE, LOGINCACHE_TABLE_COLUMNS,
                null, null, null, 0, 0);

        for (Map<String, String> resultMap : result) {
            NbLoginCacheEntity data = makeLoginCacheInfoFromTuple(resultMap);
            resultList.add(data);
        }

        log.finest("readAllLoginCache() <end> resultList={0}", resultList);
        return resultList;
    }

    private NbLoginCacheEntity makeLoginCacheInfoFromTuple(Map<String, String> tuple) {
        NbLoginCacheEntity data = new NbLoginCacheEntity();
        data.setUserId(tuple.get(USERID_COLUMN));
        data.setUserName(tuple.get(USER_NAME_COLUMN));
        data.setEmailAddress(tuple.get(EMAIL_COLUMN));
        data.setCreatedTime(tuple.get(CREATEDAT_COLUMN));
        data.setUpdatedTime(tuple.get(UPDATEDAT_COLUMN));
        data.setPasswordHash(tuple.get(PASSWORD_HASH_COLUMN));

        NbJSONObject groupListJson = NbJSONParser.parse(tuple.get(GROUPLIST_COLUMN));
        List<String> groupList = (List<String>) groupListJson.get(GROUPLIST_COLUMN);
        data.setGroupList(groupList);

        NbJSONObject options = NbJSONParser.parse(tuple.get(OPTIONS_COLUMN));
        data.setOptions(options);

        data.setLoginCacheExpire(Long.parseLong(tuple.get(LOGINCACHE_EXPIRE_COLUMN)));
        data.setSessionToken(tuple.get(SESSION_COLUMN));
        data.setSessionTokenExpire(Long.parseLong(tuple.get(SESSION_EXPIRE_COLUMN)));
        return data;
    }

    /**
     * ログインキャッシュの検索を行う。
     * @param key 検索キー。{@link #KEY_USERID}, {@link #KEY_USERNAME}, {@link #KEY_EMAIL} のいずれか。
     * @param value 検索に使用する値
     * @return ログインキャッシュ情報
     */
    public NbLoginCacheEntity readLoginCache(int key, String value) {
        log.finest("readLoginCache() <start> key={0} value={1}", key, value);
        if (value == null) return null;
        String where;

        switch (key) {
            case KEY_USERID:
                where = USERID_COLUMN + WHERE_SQL;
                break;
            case KEY_USERNAME:
                where = USER_NAME_COLUMN + WHERE_SQL;
                break;
            case KEY_EMAIL:
                where = EMAIL_COLUMN + WHERE_SQL;
                break;
            default:
                return null;
        }
        String[] whereArgs = new String[] { value };

        List<Map<String, String>> result = select(LOGINCACHE_TABLE, LOGINCACHE_TABLE_COLUMNS,
                where, whereArgs, null, 0, 0);
        NbLoginCacheEntity data = null;
        if (!result.isEmpty()) {
            Map<String, String> resultMap = result.get(0);
            data = makeLoginCacheInfoFromTuple(resultMap);
        }
        log.finest("readLoginCache() <end> data={0}", data);
        return data;
    }

    /**
     * ログインキャッシュの作成を行う。
     * @param data ログインキャッシュエンティティ
     * @return 作成したログインキャッシュ情報のid
     */
    public long createLoginCache(NbLoginCacheEntity data) {
        log.finest("createLoginCache() <start> data={0}", data);
        if (data == null) return INSERT_ERROR_CODE;

        Map<String, String> createData = makeLoginCacheTuple(data);

        long result = insert(LOGINCACHE_TABLE, createData);
        log.finest("createLoginCache() <end> result={0}", result);
        return result;
    }

    private Map<String, String> makeLoginCacheTuple(NbLoginCacheEntity data) {
        Map<String, String> tuple = new HashMap<>();
        tuple.put(USERID_COLUMN, data.getUserId());
        tuple.put(USER_NAME_COLUMN, data.getUserName());
        tuple.put(EMAIL_COLUMN, data.getEmailAddress());
        tuple.put(CREATEDAT_COLUMN, data.getCreatedTime());
        tuple.put(UPDATEDAT_COLUMN, data.getUpdatedTime());
        tuple.put(PASSWORD_HASH_COLUMN, data.getPasswordHash());
        tuple.put(GROUPLIST_COLUMN, createGroupListJson(data).toJSONString());
        tuple.put(OPTIONS_COLUMN, data.getOptions() == null ? null : data.getOptions().toJSONString());
        tuple.put(LOGINCACHE_EXPIRE_COLUMN, String.valueOf(data.getLoginCacheExpire()));
        tuple.put(SESSION_COLUMN, data.getSessionToken());
        tuple.put(SESSION_EXPIRE_COLUMN, String.valueOf(data.getSessionTokenExpire()));

        return tuple;
    }

    // Group List JSON 生成 : {"groupList": [...]} 形式
    private NbJSONObject createGroupListJson(NbLoginCacheEntity loginCacheInfo) {
        NbJSONArray<String> groupList = new NbJSONArray<>(loginCacheInfo.getGroupList());
        NbJSONObject groupListJson = new NbJSONObject();
        groupListJson.put(GROUPLIST_COLUMN, groupList);
        return groupListJson;
    }

    /**
     * ログインキャッシュの更新を行う。
     * @param userId ユーザID
     * @param data データ
     * @return 更新した行の数
     */
    public int updateLoginCache(String userId, NbLoginCacheEntity data) {
        log.finest("updateLoginCache() <start> userId={0}", userId);
        if (data == null || userId == null) return 0;

        Map<String, String> updateData = makeLoginCacheTuple(data);

        String where = USERID_COLUMN + WHERE_SQL;
        String[] whereArgs = new String[] { userId };
        int result = update(LOGINCACHE_TABLE, updateData, where, whereArgs);
        log.finest("updateLoginCache() <end> result={0}", result);
        return result;
    }

    /**
     * ログインキャッシュの更新を行う。
     * @param username ユーザ名
     * @param data データ
     * @return 更新した行の数
     */
    public int updateLoginCacheWithUsername(String username, NbLoginCacheEntity data) {
        log.finest("updateLoginCacheWithUsername() <start> username={0}", username);
        if (data == null || username == null) return 0;

        Map<String, String> updateData = makeLoginCacheTuple(data);

        String where = USER_NAME_COLUMN + WHERE_SQL;
        String[] whereArgs = new String[] { username };
        int result = update(LOGINCACHE_TABLE, updateData, where, whereArgs);
        log.finest("updateLoginCacheWithUsername() <end> result={0}", result);
        return result;
    }

    /**
     * ログインキャッシュの削除を行う。
     * @param userId ユーザID
     * @return 削除した行の数
     */
    public int deleteLoginCache(String userId) {
        log.finest("deleteLoginCache() <start> userId={0}", userId);
        if (userId == null) return 0;
        String where = USERID_COLUMN + WHERE_SQL;
        String[] whereArgs = new String[] { userId };
        int result = delete(LOGINCACHE_TABLE, where, whereArgs);
        log.finest("deleteLoginCache() <end> result={0}", result);
        return result;
    }

    /**
     * ログインキャッシュの削除を行う。
     * @param username ユーザ名
     * @param email Email
     * @return 削除した行の数
     */
    public int deleteLoginCache(String username, String email) {
        log.finest("deleteLoginCache() <start> username={0} email={1}", username, email);
        if (username == null && email == null) return 0;

        String where;
        String[] whereArgs;

        if (username != null) {
            where = USER_NAME_COLUMN + WHERE_SQL;
            whereArgs = new String[] { username };
        } else {
            where = EMAIL_COLUMN + WHERE_SQL;
            whereArgs = new String[] { email };
        }
        int result = delete(LOGINCACHE_TABLE, where, whereArgs);
        log.finest("deleteLoginCache() <end> result={0}", result);
        return result;
    }


    //------------------------------------------------------------------------------------
    // SyncCondition DAO
    //------------------------------------------------------------------------------------

    /**
     * 同期条件を保存する。
     * バケット1件のみを部分更新する。
     * @param bucketName バケット名
     * @param query 同期条件
     */
    public void saveSyncScope(@NonNull String bucketName, NbQuery query) {

        begin();

        // キャッシュ削除
        mSyncScopeCache = null;

        // 同期条件を削除して INSERT しなおす
        deleteSyncScope(bucketName);
        insertSyncScope(bucketName, query);

        commit();
    }

    /** 全 SyncScope を削除 */
    private long deleteAllSyncScopes() {
        long result = delete(SYNC_CONDITION_TABLE, null, null);
        log.finest("deleteSyncScope() result={0}", result);
        return result;
    }

    /**
     * 指定バケットの同期範囲を削除
     * @param bucketName
     */
    public void removeSyncScope(@NonNull String bucketName) {

        begin();

        // 同期範囲を削除
        deleteSyncScope(bucketName);

        commit();
    }

    /**
     * 同期条件を削除する(バケット1件)
     * @param bucketName バケット名
     */
    public void deleteSyncScope(String bucketName) {
        // キャッシュ削除
        mSyncScopeCache = null;

        String where = BUCKET_NAME_COLUMN + WHERE_SQL;
        String[] whereArgs = new String[] { bucketName };
        long result = delete(SYNC_CONDITION_TABLE, where, whereArgs);
        log.finest("deleteSyncScope() result={0}", result);
    }

    /** SyncScope を追加 */
    private long insertSyncScope(String bucketName, NbQuery query) {
        Map<String, String> dbMap = new HashMap<>();
        if (query == null) {
            query = new NbQuery();
        }
        //clause
        NbClause clause = query.getClause();
        NbJSONObject conditions = null;
        if (clause != null) {
            conditions = clause.getConditions();
        }
        if (conditions == null) {
            conditions = new NbJSONObject(); // fail safe
        }
        //limit
        String limit = String.valueOf(query.getLimit());
        //skipCount
        String skipCount = String.valueOf(query.getSkipCount());
        //count
        String count = String.valueOf(query.getCountQueryAsNum());
        //sortOrder
        Map<String, Boolean> sortOrderMap = query.getSortOrder();
        String sortOrder = NbJSONGenerator.jsonToString(sortOrderMap);
        //deleteMark
        String deleteMark = String.valueOf(query.isDeleteMark());

        log.finest("saveSyncScope() bucketName={0}", bucketName);
        log.finest("saveSyncScope() conditions={0}", conditions);
        log.finest("saveSyncScope() limit={0}", limit);
        log.finest("saveSyncScope() skipCount={0}", skipCount);
        log.finest("saveSyncScope() count={0}", count);
        log.finest("saveSyncScope() sortOrder={0}", sortOrder);
        log.finest("saveSyncScope() deleteMark={0}", deleteMark);

        dbMap.put(BUCKET_NAME_COLUMN, bucketName);
        dbMap.put(QUERY_CONDITIONS_COLUMN, conditions.toJSONString());
        dbMap.put(QUERY_LIMIT_COLUMN, limit);
        dbMap.put(QUERY_SKIPCOUNT_COLUMN, skipCount);
        dbMap.put(QUERY_COUNT_COLUMN, count);
        dbMap.put(QUERY_SORTORDER_COLUMN, sortOrder);
        dbMap.put(QUERY_DELETEMARK_COLUMN, deleteMark);

        log.finest("saveSyncScope() dbMap={0}", dbMap);

        //1レコード分のカラムの作成が完了したらレコード単位で格納
        return insert(SYNC_CONDITION_TABLE, dbMap);
    }

    /**
     * 全同期範囲を取得する
     * @return 同期範囲の Map (バケット名がキー)
     */
    public Map<String, NbQuery> readSyncScope() {
        log.finest("readSyncScope() <start>");

        // キャッシュがある場合はそちらを返す。
        if (mSyncScopeCache != null) {
            log.finest("readSyncScope() <end> - Cache hit.");
            return mSyncScopeCache;
        }

        List<Map<String, String>> rows = select(SYNC_CONDITION_TABLE,
                null, null, null, null, 0, 0);

        Map<String, NbQuery> syncScope = new HashMap<>();

        //レコード単位のループ
        for (Map<String, String> row: rows) {
            String bucketName = row.get(BUCKET_NAME_COLUMN);
            log.finest("readSyncScope() bucketName={0}", bucketName);

            NbQuery query = syncConditionRowToQuery(row);

            //MAPへ追加
            syncScope.put(bucketName, query);
        }

        mSyncScopeCache = syncScope;

        log.finest("readSyncScope() <end>");
        return syncScope;
    }

    /**
     * 同期範囲を取得する
     * @param bucketName バケット名
     * @return バケットの同期範囲
     */
    public NbQuery readSyncScope(String bucketName) {
        Map<String, NbQuery> scopes = readSyncScope();
        return scopes.get(bucketName);
    }

    private NbQuery syncConditionRowToQuery(Map<String, String> row) {
        //各カラム取得
        String conditionsStr = row.get(QUERY_CONDITIONS_COLUMN);
        NbJSONObject conditions = null;
        if (conditionsStr != null) {
            conditions = NbJSONParser.parse(conditionsStr);
        }
        if (conditions == null){
            conditions = new NbJSONObject(); // fail safe
        }
        String limit = row.get(QUERY_LIMIT_COLUMN);
        String skipCount = row.get(QUERY_SKIPCOUNT_COLUMN);
        String count = row.get(QUERY_COUNT_COLUMN);
        String sortOrderStr = row.get(QUERY_SORTORDER_COLUMN);
        NbJSONObject sortOrder = NbJSONParser.parse(sortOrderStr);
        String deleteMark = row.get(QUERY_DELETEMARK_COLUMN);

        log.finest("readSyncScope() conditions={0}", conditions);
        log.finest("readSyncScope() limit={0}", limit);
        log.finest("readSyncScope() skipCount={0}", skipCount);
        log.finest("readSyncScope() count={0}", count);
        log.finest("readSyncScope() sortOrder={0}", sortOrder);
        log.finest("readSyncScope() deleteMark={0}", deleteMark);

        //Clause復元
        NbClause clause = NbClause.fromJSONObject(conditions);

        //Query復元
        NbQuery query = new NbQuery();
        query.setClause(clause);
        if (isNumber(limit)) query.setLimit(Integer.parseInt(limit));
        if (isNumber(skipCount)) query.setSkipCount(Integer.parseInt(skipCount));
        if (isNumber(count)) query.setCountQuery(Integer.parseInt(count) != 0);
        //sortOrderはnullにならない
        for (Map.Entry<String,Object> entry : sortOrder.entrySet()) {
            query.addSortOrder(entry.getKey(), (Boolean)entry.getValue());
        }
        //parseBooleanはnullでも例外ではなくfalseになるので直にチェック。
        if (deleteMark != null) {
            query.setDeleteMark(Boolean.parseBoolean(deleteMark));
        }

        //log.finest("readSyncScope() bucketName={0} conditions={1}",
        //        bucketName, query.getClause().getConditions());

        return query;
    }

    /**
     * 前回同期時刻の保存を行う。<br>
     * @param bucketName : String
     * @param syncTime : String
     * @return 更新した行の数
     */
    public int updateLastSyncTime(String bucketName, String syncTime) {
        return updateSyncConditionTableTime(bucketName, TIMESTAMP_COLUMN, syncTime);
    }

    /**
     * 前回同期時刻の取得を行う。<br>
     * @param bucketName : String
     * @return 更新した行の数
     */
    public String getLastSyncTime(String bucketName) {
        return getSyncConditionTableTime(bucketName, TIMESTAMP_COLUMN);
    }

    /**
     * 前回同期時刻の削除を行う。<br>
     * @param bucketName : String
     * @return 削除した行の数
     */
    public int removeLastSyncTime(String bucketName) {
        return removeSyncConditionTableTime(bucketName, TIMESTAMP_COLUMN);
    }

    /**
     * サーバPull時刻の保存を行う。<br>
     * @param bucketName : String
     * @param syncTime : String
     * @return 更新した行の数
     */
    public int updateLatestPullServerTime(String bucketName, String syncTime) {
        return updateSyncConditionTableTime(bucketName, TIMESTAMP_PULL_SERVER_TIME_COLUMN, syncTime);
    }

    /**
     * サーバPull時刻の取得を行う。<br>
     * @param bucketName : String
     * @return 更新した行の数
     */
    public String getLatestPullServerTime(String bucketName) {
        return getSyncConditionTableTime(bucketName, TIMESTAMP_PULL_SERVER_TIME_COLUMN);
    }

    /**
     * サーバPull時刻の削除を行う。<br>
     * @param bucketName : String
     * @return 削除した行の数
     */
    public int removeLatestPullServerTime(String bucketName) {
        return removeSyncConditionTableTime(bucketName, TIMESTAMP_PULL_SERVER_TIME_COLUMN);
    }

    /**
     * 時刻情報の保存を行う。<br>
     * @param bucketName : String
     * @param columnName 保存対象カラム名
     * @param time : String
     * @return 更新した行の数
     */
    private int updateSyncConditionTableTime(@NonNull String bucketName, @NonNull String columnName, @NonNull String time) {

        //時刻のフォーマットの検証は行わない
        int result = 0;

        //更新データ生成
        Map<String, String> updateData = new HashMap<>();
        updateData.put(columnName, time);

        //更新条件生成
        String where = BUCKET_NAME_COLUMN + WHERE_SQL;
        String[] whereArg = new String[]{bucketName};

        try {
            //DB更新
            result = update(SYNC_CONDITION_TABLE, updateData, where, whereArg);
        } catch (NbDatabaseException e) {
            log.severe("updateSyncConditionTableTime()"
                    + " notice update fail e=" + e);
        }

        return result;
    }

    /**
     * 時刻情報の取得を行う。<br>
     * @param bucketName : String
     * @param columnName 取得対象のカラム名
     * @return 更新した行の数
     */
    private String getSyncConditionTableTime(@NonNull String bucketName, @NonNull String columnName) {

        String where = BUCKET_NAME_COLUMN + WHERE_SQL;
        String[] whereArg = new String[]{bucketName};

        List<Map<String, String>> result = null;
        //DB取得
        try {
            result = select(SYNC_CONDITION_TABLE, null, where, whereArg, null, 0, 0);
        } catch (NbDatabaseException e) {
            log.severe("getSyncConditionTableTime() ERR select err e=" + e);
        }

        String time = null;
        if (result != null && !result.isEmpty()) {
            Map<String, String> resultMap = result.get(0);
            log.finest("getSyncConditionTableTime() resultMap={0}", resultMap);
            time = resultMap.get(columnName);
        }

        return time;
    }

    /**
     * 時刻情報の削除を行う。<br>
     * @param bucketName : String
     * @param columnName : 取得対象カラム名
     * @return 削除した行の数
     */
    private int removeSyncConditionTableTime(@NonNull String bucketName, @NonNull String columnName) {

        int result = 0;
        //前回同期時刻を削除（null初期値）
        Map<String, String> updateData = new HashMap<String, String>();
        updateData.put(BUCKET_NAME_COLUMN, bucketName);
        updateData.put(columnName, null);

        //更新条件生成
        String where = BUCKET_NAME_COLUMN + WHERE_SQL;
        String[] whereArg = new String[]{bucketName};

        try {
            //DB更新
            result = update(SYNC_CONDITION_TABLE, updateData, where, whereArg);
        } catch (NbDatabaseException e) {
            log.severe("removeSyncConditionTableTime() notice update fail e=" + e);
        }

        return result;
    }


    //------------------------------------------------------------------------------------
    // SyncManage DAO
    //------------------------------------------------------------------------------------

//    /**
//     * 同期間隔時刻の保存を行う。<br>
//     * @param bucketName バケット名
//     * @param interval 同期間隔時刻
//     */
//    public void saveAutoSyncInterval(String bucketName, long interval) {
//        log.finest("saveAutoSyncInterval() <start> bucketName=" + bucketName
//                + " interval={0}",interval);
//        if (bucketName == null || interval == 0) throw new IllegalArgumentException();
//
//        // bucketNameがユニークなので、重複登録は発生しない
//        insertSyncInterval(bucketName, interval);
//
//        log.finest("saveAutoSyncInterval() <end>");
//    }
//
//    /**
//     * 同期間隔を挿入する
//     * @param bucketName バケット名
//     * @param interval 同期間隔
//     */
//    protected long insertSyncInterval(String bucketName, long interval) {
//        log.finest("insertSyncInterval() <start> bucketName=" + bucketName + " interval=" + interval);
//
//        Map<String, String> dbMap = new HashMap<>();
//        dbMap.put(BUCKET_NAME_COLUMN, bucketName);
//        dbMap.put(INTERVAL_COLUMN, String.valueOf(interval));
//
//        long result = insert(SYNC_MANAGE_TABLE, dbMap);
//        log.finest("insertSyncInterval() <end> result=" + result);
//        return result;
//    }
//
//    /**
//     * 同期間隔時間の取得を行う。
//     * @param bucketName バケット名
//     * @return 同期間隔（秒単位）
//     */
//    public long readAutoSyncInterval(String bucketName) {
//        log.finest("readAutoSyncInterval() <start> bucketName=" + bucketName);
//        if (bucketName == null) throw new IllegalArgumentException();
//
//        // 検索失敗(未格納)の場合は同期間隔未設定として扱う
//        long interval = -1;
//
//        String where = BUCKET_NAME_COLUMN + WHERE_SQL;
//        // バケット名をキーに検索
//        String[] whereArgs = new String[] { bucketName };
//        List<Map<String, String>> rows = select(SYNC_MANAGE_TABLE, SYNC_MANAGE_TABLE_COLUMNS, where, whereArgs, null, 0, 0);
//        if(rows != null && !rows.isEmpty()) {
//            log.finest("success to select rows=" + rows);
//            // 検索成功
//            // bucketNameはユニークなので、検索がヒットする要素は一つだけ
//            Map<String, String> resultMap = rows.get(0);
//            String rawInterval = resultMap.get(INTERVAL_COLUMN);
//            if(rawInterval != null) {
//                interval = Long.parseLong(rawInterval);
//            }
//            log.finest("raw interval :" + rawInterval);
//        }
//        log.finest("readAutoSyncInterval() <end> interval={0}", interval);
//
//        return interval;
//    }


    //------------------------------------------------------------------------------------
    // NbFileMetadata DAO
    //------------------------------------------------------------------------------------

    /**
     * ファイルメタデータの作成を行う。
     * @param bucketName バケット名
     * @param data MetaData Entity
     * @return 作成したメタデータの_id
     */
    /*
    public long createMetadata(String bucketName, NbFileMetadataEntity data) {
        log.finest("createMetadata() <start>"
                + " bucketName={0}", bucketName);

        if (data == null || bucketName == null)
            throw new IllegalArgumentException();

        Map<String, String> tuple = makeFileMetadataTuple(data);
        log.finest("createMetadata() map data={0}", tuple);

        long result = insert(FILE_BUCKET_TBL_PREFIX + bucketName, tuple);
        log.finest("createMetadata() <end> result={0}", result);
        return result;
    }

    private Map<String, String> makeFileMetadataTuple(NbFileMetadataEntity data) {
        Map<String, String> mapData = new HashMap<String, String>();

        mapData.put(FILENAME_COLUMN, data.getFileName());
        mapData.put(META_STATE_COLUMN, data.getMetaState().idString);
        mapData.put(FILE_STATE_COLUMN, data.getFileState().idString);
        mapData.put(CREATE_TIME_COLUMN, data.getCreatedTime());
        mapData.put(UPDATE_TIME_COLUMN, data.getUpdatedTime());
        mapData.put(META_ETAG_COLUMN, data.getMetaETag());
        mapData.put(FILE_ETAG_COLUMN, data.getFileETag());
        mapData.put(CACHE_DISABLE_COLUMN, String.valueOf(data.isCacheDisabled()));
        mapData.put(PUBLIC_URL_COLUMN, data.getPublicUrl());
        mapData.put(PERMISSION_COLUMN, data.getAclString());
        mapData.put(FILESIZE_COLUMN,  String.valueOf(data.getFileSize()));
        mapData.put(CONTENT_TYPE_COLUMN, data.getContentType());
        mapData.put(META_ID_COLUMN, data.getMetaId());
        return mapData;
    }

    private NbFileMetadataEntity makeFileMetadataInfo(Map<String, String> tuple) {
        NbFileMetadataEntity info = new NbFileMetadataEntity();
        info.setDbId(Integer.parseInt(tuple.get(ID_COLUMN)));
        info.setFileName(tuple.get(FILENAME_COLUMN));
        info.setMetaState(NbSyncState.fromObject(tuple.get(META_STATE_COLUMN)));
        info.setFileState(NbSyncState.fromObject(tuple.get(FILE_STATE_COLUMN)));
        info.setCreatedTime(tuple.get(CREATE_TIME_COLUMN));
        info.setUpdatedTime(tuple.get(UPDATE_TIME_COLUMN));
        info.setMetaETag(tuple.get(META_ETAG_COLUMN));
        info.setFileETag(tuple.get(FILE_ETAG_COLUMN));
        info.setCacheDisabled(Boolean.parseBoolean(tuple.get(CACHE_DISABLE_COLUMN)));
        info.setPublicUrl(tuple.get(PUBLIC_URL_COLUMN));
        info.setAclString(tuple.get(PERMISSION_COLUMN));
        info.setFileSize(Long.parseLong(tuple.get(FILESIZE_COLUMN)));
        info.setContentType(tuple.get(CONTENT_TYPE_COLUMN));
        info.setMetaId(tuple.get(META_ID_COLUMN));
        return info;
    }
    */

    /**
     * ファイルメタデータの読み込みを行う。
     * @param fileName ファイル名
     * @param bucketName バケット名
     * @param hasDeleted 削除データを含むか否か
     * @return 読み込んだメタデータ
     */
    /*
    public NbFileMetadataEntity readMetadataKeyFilename(String fileName, String bucketName,
                                                    boolean hasDeleted) {
        return readMetadata(fileName, FILENAME_COLUMN, bucketName, hasDeleted);
    }

    protected NbFileMetadataEntity readMetadataKeyMetaId(String metaId, String bucketName,
            boolean hasDeleted) {
        return readMetadata(metaId, META_ID_COLUMN, bucketName, hasDeleted);
    }

    protected NbFileMetadataEntity readMetadataKeyDbId(int id, String bucketName,
            boolean hasDeleted) {
        return readMetadata(String.valueOf(id), ID_COLUMN, bucketName, hasDeleted);
    }

    protected NbFileMetadataEntity readMetadata(String key, String keyColumn, String bucketName,
            boolean hasDeleted) {
        if (key == null || keyColumn == null || bucketName == null) {
            throw new IllegalArgumentException();
        }

        String where = null;
        String[] whereArg = null;

        if (hasDeleted) {
            where = keyColumn + WHERE_SQL;    //削除データ含む
            whereArg = new String[]{key};
        } else {
            where = createSqlWithoutDeleteData(keyColumn);       //削除データ除く
            String state = NbSyncState.DELETE.idString;
            whereArg = new String[]{key, state};
        }

        //ファイル取得
        List<Map<String, String>> result = select((FILE_BUCKET_TBL_PREFIX + bucketName),
                FILE_METADATA_TABLE_COLUMNS, where, whereArg, null, 0, 0);
        NbFileMetadataEntity data = null;
        if (result != null && !result.isEmpty()) {
            Map<String, String> resultMap = result.get(0);
            //DataInfo化
            data = makeFileMetadataInfo(resultMap);
        }
        return data;
    }
    */

    private String createSqlWithoutDeleteData(String columnName) {
        return columnName + " = ? and not " + META_STATE_COLUMN + " = ?;";
    }

    /**
     * メタデータ一覧取得を行う。
     * @param bucketName バケット名
     * @param hasDeleted     削除データ含むか否か
     * @return 一覧取得結果
     */
    /*
    public List<NbFileMetadataEntity> readMetadataList(String bucketName, boolean hasDeleted) {
        log.finest("readMetadataList() <start>"
                + " bucketName={0} hasDeleted={1}", bucketName, hasDeleted);
        if (bucketName == null) {
            throw new IllegalArgumentException();
        }

        String where = null;
        String[] whereArg = null;

        //削除データは含まない
        if (!hasDeleted) {
            where = " not " + META_STATE_COLUMN + WHERE_SQL;
            String state = NbSyncState.DELETE.idString;
            whereArg = new String[]{state};
        }

        List<Map<String, String>> resultList = select((FILE_BUCKET_TBL_PREFIX + bucketName),
                FILE_METADATA_TABLE_COLUMNS, where, whereArg, null, 0, 0);

        List<NbFileMetadataEntity> metaList = new ArrayList<>();
        for (Map<String, String> result: resultList) {
            NbFileMetadataEntity data = makeFileMetadataInfo(result);
            metaList.add(data);
        }
        log.finest("readMetadataList() <end>"
                + " metaList.size={0}", metaList.size());
        return metaList;
    }
    */

    /**
     * ファイルメタデータの更新を行う。
     * @param bucketName バケット名
     * @param data データ
     * @return 更新を行った行の数
     */
    /*
    public int updateMetadata(String bucketName, NbFileMetadataEntity data) {
        if (data == null || bucketName == null) {
          throw new IllegalArgumentException();
        }

        //MAP化
        Map<String, String> mapData = makeFileMetadataTuple(data);
        log.finest("updateMetadata() map data={0}", mapData);

        String where = null;
        String[] whereArg = null;
        if (data.getMetaId() != null) {
            //サーバ同期済みデータを更新する場合はメタIDをキーに検索更新
            where = META_ID_COLUMN + WHERE_SQL;
            whereArg = new String[]{String.valueOf(data.getMetaId())};
        } else {
            //未同期データを更新する場合はDBのIDをキーに検索更新
            //(オフラインで作成→更新のケース)
            where = ID_COLUMN + WHERE_SQL; //_IDをキーに検索更新
            whereArg = new String[]{String.valueOf(data.getDbId())};
        }

        //ファイル更新
        int result = update((FILE_BUCKET_TBL_PREFIX + bucketName), mapData, where, whereArg);

        log.finest("updateMetadata() result={0} where={1} whereArgs={2}"
                , result, where, Arrays.toString(whereArg) );

        return result;
    }
    */

    /**
     * ファイルメタデータの更新を行う。（対象検索方法＝ファイル異名）
     * @param bucketName バケット名
     * @param data データ
     * @return 更新を行った行の数
     */
    /*
    public int updateMetadataByFileName(String bucketName, NbFileMetadataEntity data) {
        if (data == null || bucketName == null) {
            throw new IllegalArgumentException();
        }

        //MAP化
        Map<String, String> mapData = makeFileMetadataTuple(data);
        log.finest("updateMetadataByFileName() map data={0}", mapData);


        String where = FILENAME_COLUMN + WHERE_SQL;
        String[] whereArg = new String[]{String.valueOf(data.getFileName())};

        //ファイル更新
        int result = update((FILE_BUCKET_TBL_PREFIX + bucketName), mapData, where, whereArg);
        return result;
    }
    */

    /**
     * ファイルメタデータの削除を行う
     * @param fileName ファイル名
     * @param bucketName バケット名
     * @return 削除した行の数
     */
    /*
    public int deleteMetadata(String fileName, String bucketName) {
        log.finest("deleteMetadata() <start>"
                + " fileName=" + fileName + " bucketName=" + bucketName);

        if (fileName == null || bucketName == null) throw new IllegalArgumentException();

        String where = FILENAME_COLUMN + WHERE_SQL; //ファイル名をキーに検索削除
        String[] whereArg = new String[]{fileName};
        //ファイル削除
        int result = delete((FILE_BUCKET_TBL_PREFIX + bucketName), where, whereArg);
        log.finest("deleteMetadata() <end> result=" + result);
        return result;
    }
    */

    //------------------------------------------------------------------------------------
    // Utilities
    //------------------------------------------------------------------------------------

    /**
     * Number型か否かのチェック。
     */
    private boolean isNumber(String val) {
        try {
            Integer.parseInt(val);
            return true;
        } catch (NumberFormatException nfex) {
            return false;
        }
    }

    /**
     * 管理テーブル名を取得
     * @param isFile true - ファイル管理テーブル(fileBucketManage), false - オブジェクト管理テーブル(bucketManage)
     * @return 管理テーブル名
     */
    private String getManageTableName(boolean isFile) {
        return (isFile) ? FILE_BUCKET_MANAGE_TABLE : BUCKET_MANAGE_TABLE;
    }

    /**
     * バケットテーブルプレフィクスを取得
     * @param isFile true - ファイルテーブル(FILE_)、false - オブジェクトテーブル(OBJECT_)
     * @return プレフィクス
     */
    private String getBucketTablePrefixName(boolean isFile) {
        return (isFile) ? FILE_BUCKET_TBL_PREFIX : OBJECT_BUCKET_TBL_PREFIX;
    }

    /**
     * バケットテーブルカラム定義を取得
     * @param isFile true - ファイルテーブル定義、false - オブジェクトテーブル定義
     * @return カラム定義
     */
    private LinkedHashMap<String,String> getTableColumnsDef(boolean isFile) {
        return (isFile) ? FILE_METADATA_TABLE_COLUMNS_DEF : OBJECT_TABLE_COLUMNS_DEF;
    }

    /**
     * バケットテーブルのカラムリストを取得
     * @param isFile true - ファイルテーブル定義、false - オブジェクトテーブル定義
     * @return カラムリスト
     */
    private String[] getTableColumns(boolean isFile) {
        return (isFile) ? FILE_METADATA_TABLE_COLUMNS : OBJECT_TABLE_COLUMNS;
    }

    /**
     * オブジェクト格納用テーブル名取得
     * @param bucketName バケット名
     * @return テーブル名
     */
    private String getObjectTableName(String bucketName) {
        return OBJECT_BUCKET_TBL_PREFIX + bucketName;
    }

    /**
     * オブジェクトテーブルかどうかを判定
     * @param table テーブル名
     * @return true - オブジェクトテーブルである、false - それ以外
     */
    private boolean isObjectTableName(String table) {
        return table.startsWith(OBJECT_BUCKET_TBL_PREFIX);
    }

    //------------------------------------------------------------------------------------
    // NbDatabaseWrapper
    //------------------------------------------------------------------------------------
    @Override
    public void begin() {
        if (mDataSecurityHook != null) {
            mDataSecurityHook.begin();
        }
    }

    @Override
    public void commit() {
        if (mDataSecurityHook != null) {
            mDataSecurityHook.commit();
        }
    }

    @Override
    public void rollback() {
        if (mDataSecurityHook != null) {
            mDataSecurityHook.rollback();
        }
    }

    /**
     * テーブルの内容をダンプする(デバッグ用)
     * @param tableName
     */
    public void _debugDumpTable(String tableName) {
        log.fine("--- TABLE = " + tableName + " ---");

        CursorWrapper cursor = selectForCursor(tableName, null, null, null, null, 0, 0);
        _debugDumpCursor(cursor);
        cursor.close();
    }

    /**
     * カーソルの内容をダンプする(デバッグ用)
     * @param cursor
     */
    public void _debugDumpCursor(CursorWrapper cursor) {
        boolean hasNext = cursor.moveToFirst();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            if (i > 0) sb.append("|");
            sb.append(cursor.getColumnName(i));
        }
        log.fine(sb.toString());

        while (hasNext) {
            int n = cursor.getColumnCount();
            sb = new StringBuilder();

            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append("|");
                sb.append(cursor.getString(i));
            }
            log.fine(sb.toString());
            hasNext = cursor.moveToNext();
        }
    }
}

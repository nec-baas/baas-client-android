/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.util;

import com.nec.baas.core.*;

import java.text.MessageFormat;
import java.util.logging.Level;

/**
 * ロガー。
 *
 * Android では {@link android.util.Log} が、Pure Java では
 * {@link java.util.logging.Logger} が使われる。
 *
 * ログレベルの指定は、{@link java.util.logging.Level} と同等。
 *
 * <p>使用方法:
 * <pre>
 * // ロガーを生成
 * private static final NbLogger log = NbLogger.getLogger(Foo.class);
 *
 * public void someFunc() {
 *     // ログ出力
 *     log.fine("Log message: var1={0}", var1);
 *     ...
 * }
 * </pre>
 * @since 1.2.0
 */
public abstract class NbLogger {
    // Logger 名の最大長。
    // 23文字は Android Log の仕様。
    public static final int MAX_NAME_LENGTH = 23;

    // NbLogger ファクトリ
    public interface LoggerFactory {
        NbLogger create(String name);
    }

    protected static LoggerFactory sFactory;

    static {
        try {
            // ファクトリ初期化。
            // Android 用の Logger の使用を試みる
            Class<LoggerFactory> factory = (Class<LoggerFactory>)Class.forName("com.nec.baas.util.NbAndroidLoggerFactory");
            sFactory = factory.newInstance();
        } catch (Exception e) {
            sFactory = new NbGenericLogger.Factory();
        }
    }

    /**
     * LoggerFactory を差し替える。
     * @param factory LoggerFactory
     */
    public static void setLoggerFactory(LoggerFactory factory) {
        sFactory = factory;
    }

    /**
     * ロガーを取得する
     * @param cls クラス名
     * @return ロガー
     */
    public static NbLogger getLogger(Class cls) {
        return getLogger(cls.getSimpleName());
    }

    /**
     * ロガーを取得する
     * @param name ロガー種別(タグ名)
     * @return ロガー
     */
    public static NbLogger getLogger(String name) {
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);

            //final String errMsg = "Too long name: " + name;
            //System.err.println(errMsg);
            //throw new IllegalArgumentException(errMsg);
        }
        return sFactory.create(name);
    }

    protected NbLogger() {
    }

    protected abstract void printLog(Level level, String msg, Object... params);

    protected String formatMessage(String msg, Object... params) {
        if (params.length == 0) {
            return msg;
        } else {
            try {
                return MessageFormat.format(msg, params);
            } catch (IllegalArgumentException e) {
                // 例外にはせずパラメータは無視
                return msg;
            }
        }
    }

    /**
     * ログを出力する。
     * Operation Mode が DEBUG でない場合、FINER, FINEST ログは
     * 出力しない。
     * @param level レベル
     * @param msg メッセージ
     * @param params パラメータ
     */
    public void log(Level level, String msg, Object... params) {
        if (NbSetting.getOperationMode() != NbOperationMode.DEBUG
                && level.intValue() < Level.FINER.intValue()) {
            // デバッグモードでない場合、FINER, FINEST ログは出力しない
            return;
        }
        printLog(level, msg, params);
    }

    /**
     * SEVERE ログを出力する
     * @param msg メッセージ
     * @param params パラメータ
     */
    public void severe(String msg, Object... params) {
        log(Level.SEVERE, msg, params);
    }

    /**
     * WARNING ログを出力する
     * @param msg メッセージ
     * @param params パラメータ
     */
    public void warning(String msg, Object... params) {
        log(Level.WARNING, msg, params);
    }

    /**
     * INFO ログを出力する
     * @param msg メッセージ
     * @param params パラメータ
     */
    public void info(String msg, Object... params) {
        log(Level.INFO, msg, params);
    }

    /**
     * FINE ログを出力する
     * @param msg メッセージ
     * @param params パラメータ
     */
    public void fine(String msg, Object... params) {
        log(Level.FINE, msg, params);
    }

    /**
     * FINER ログを出力する
     * @param msg メッセージ
     * @param params パラメータ
     */
    public void finer(String msg, Object... params) {
        log(Level.FINER, msg, params);
    }

    /**
     * FINEST ログを出力する
     * @param msg メッセージ
     * @param params パラメータ
     */
    public void finest(String msg, Object... params) {
        log(Level.FINEST, msg, params);
    }
}

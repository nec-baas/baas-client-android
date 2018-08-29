/*
 * NEC Mobile Backend Platform
 *
 * Copyright (c) 2013-2018, NEC Corporation.
 */

package com.nec.baas.offline.internal;

/**
 * NbDatabaseManager フック
 */
public interface DatabaseHook {
    void deleteBucket(String bucketName);
    void createObject(String bucketName, String objectId, NbObjectEntity data);
    void updateObject(String bucketName, String objectId, NbObjectEntity data);
    void deleteObject(String bucketName, String objectId, NbObjectEntity data);
    void begin();
    void commit();
    void rollback();
}

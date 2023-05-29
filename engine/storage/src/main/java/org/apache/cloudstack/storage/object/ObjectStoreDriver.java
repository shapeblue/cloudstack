/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.storage.object;

import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.BucketPolicy;
import com.cloud.storage.Bucket;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreDriver;

import java.util.List;

public interface ObjectStoreDriver extends DataStoreDriver {
    Bucket createBucket(Bucket bucket, boolean objectLock);

    List<Bucket> listBuckets(long storeId);

    boolean deleteBucket(String bucketName, long storeId);

    AccessControlList getBucketAcl(String bucketName, long storeId);

    void setBucketAcl(String bucketName, AccessControlList acl, long storeId);

    void setBucketPolicy(String bucketName, String policyType, long storeId);

    BucketPolicy getBucketPolicy(String bucketName, long storeId);

    void deleteBucketPolicy(String bucketName, long storeId);

    boolean createUser(long accountId, long storeId);

    boolean setBucketEncryption(String bucketName, long storeId);

    boolean deleteBucketEncryption(String bucketName, long storeId);


    boolean setBucketVersioning(String bucketName, long storeId);

    boolean deleteBucketVersioning(String bucketName, long storeId);

    void setBucketQuota(String bucketName, long storeId, long size);
}

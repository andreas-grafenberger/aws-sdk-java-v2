/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.enhanced.dynamodb.functionaltests;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.awssdk.enhanced.dynamodb.functionaltests.AttributeNamingStrategyLocalChecks.ID;
import static software.amazon.awssdk.enhanced.dynamodb.functionaltests.AttributeNamingStrategyLocalChecks.INDEX;
import static software.amazon.awssdk.enhanced.dynamodb.functionaltests.AttributeNamingStrategyLocalChecks.NAME;
import static software.amazon.awssdk.enhanced.dynamodb.functionaltests.AttributeNamingStrategyLocalChecks.OTHER_ID;
import static software.amazon.awssdk.enhanced.dynamodb.functionaltests.AttributeNamingStrategyLocalChecks.assertKeySchema;
import static software.amazon.awssdk.enhanced.dynamodb.functionaltests.AttributeNamingStrategyLocalChecks.assertStoredNames;
import static software.amazon.awssdk.enhanced.dynamodb.functionaltests.AttributeNamingStrategyLocalChecks.createTableRequest;
import static software.amazon.awssdk.enhanced.dynamodb.functionaltests.AttributeNamingStrategyLocalChecks.filtered;
import static software.amazon.awssdk.enhanced.dynamodb.functionaltests.AttributeNamingStrategyLocalChecks.indexQuery;
import static software.amazon.awssdk.enhanced.dynamodb.functionaltests.AttributeNamingStrategyLocalChecks.readItem;
import static software.amazon.awssdk.enhanced.dynamodb.functionaltests.AttributeNamingStrategyLocalChecks.sortQuery;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.functionaltests.NamingStrategySchemas.ImmutableRecord;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;

public class AsyncStaticImmutableTableSchemaAttributeNamingStrategyTest extends LocalDynamoDbAsyncTestBase {
    private final String tableName = getConcreteTableName("naming-strategy");
    private final DynamoDbEnhancedAsyncClient enhancedClient = DynamoDbEnhancedAsyncClient.builder()
        .dynamoDbClient(getDynamoDbAsyncClient())
        .build();
    private final DynamoDbAsyncTable<ImmutableRecord> mappedTable =
        enhancedClient.table(tableName, NamingStrategySchemas.immutableSchema());

    @Before
    public void createTable() {
        mappedTable.createTable(createTableRequest(getDefaultProvisionedThroughput())).join();
    }

    @After
    public void deleteTable() {
        getDynamoDbAsyncClient().deleteTable(DeleteTableRequest.builder().tableName(tableName).build()).join();
    }

    @Test
    public void putAndGet_storeTheTransformedNames() {
        mappedTable.putItem(NamingStrategySchemas.immutableRecord(ID)).join();

        try (DynamoDbClient syncClient = localDynamoDb().createClient()) {
            assertStoredNames(readItem(syncClient, tableName, ID));
            assertKeySchema(syncClient, tableName);
        }
    }

    @Test
    public void queryAndFilter_useTheStoredName() {
        mappedTable.putItem(NamingStrategySchemas.immutableRecord(ID)).join();

        assertThat(drainPublisher(mappedTable.query(sortQuery()), 1).get(0).items()).hasSize(1);
        assertThat(drainPublisher(mappedTable.index(INDEX).query(indexQuery()), 1).get(0).items()).hasSize(1);
        assertThat(drainPublisher(mappedTable.query(filtered(NAME)), 1).get(0).items()).hasSize(1);
        assertThat(drainPublisher(mappedTable.query(filtered("customerName")), 1).get(0).items()).isEmpty();
    }

    @Test
    public void synchronousAndAsynchronousPuts_storeTheSameNames() {
        try (DynamoDbClient syncClient = localDynamoDb().createClient()) {
            DynamoDbEnhancedClient syncEnhanced = DynamoDbEnhancedClient.builder().dynamoDbClient(syncClient).build();
            DynamoDbTable<ImmutableRecord> syncTable =
                syncEnhanced.table(tableName, NamingStrategySchemas.immutableSchema());
            syncTable.putItem(NamingStrategySchemas.immutableRecord(OTHER_ID));
            mappedTable.putItem(NamingStrategySchemas.immutableRecord(ID)).join();

            assertThat(readItem(syncClient, tableName, ID).keySet())
                .isEqualTo(readItem(syncClient, tableName, OTHER_ID).keySet());
        }
    }
}

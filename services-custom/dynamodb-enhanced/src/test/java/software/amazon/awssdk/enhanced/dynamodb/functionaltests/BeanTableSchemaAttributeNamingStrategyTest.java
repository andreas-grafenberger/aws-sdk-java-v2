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
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.functionaltests.models.NamingStrategyBean;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;

public class BeanTableSchemaAttributeNamingStrategyTest extends LocalDynamoDbSyncTestBase {
    private final String tableName = getConcreteTableName("naming-strategy");
    private final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
        .dynamoDbClient(getDynamoDbClient())
        .build();
    private final DynamoDbTable<NamingStrategyBean> mappedTable =
        enhancedClient.table(tableName, NamingStrategyBean.schema());

    @Before
    public void createTable() {
        mappedTable.createTable(createTableRequest(getDefaultProvisionedThroughput()));
    }

    @After
    public void deleteTable() {
        getDynamoDbClient().deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
    }

    @Test
    public void putAndGet_storeTheTransformedNames() {
        mappedTable.putItem(NamingStrategyBean.item(ID));

        assertStoredNames(readItem(getDynamoDbClient(), tableName, ID));
        assertKeySchema(getDynamoDbClient(), tableName);
    }

    @Test
    public void queryAndFilter_useTheStoredName() {
        mappedTable.putItem(NamingStrategyBean.item(ID));

        assertThat(mappedTable.query(sortQuery()).iterator().next().items()).hasSize(1);
        assertThat(mappedTable.index(INDEX).query(indexQuery()).iterator().next().items()).hasSize(1);
        assertThat(mappedTable.query(filtered(NAME)).iterator().next().items()).hasSize(1);
        assertThat(mappedTable.query(filtered("customerName")).iterator().next().items()).isEmpty();
    }
}

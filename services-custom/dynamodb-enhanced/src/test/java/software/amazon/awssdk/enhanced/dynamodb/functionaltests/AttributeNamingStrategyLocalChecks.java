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

import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

public final class AttributeNamingStrategyLocalChecks {
    public static final String INDEX = "byStatus";
    public static final String PARTITION = "PK";
    public static final String SORT = "order_date";
    public static final String STATUS = "customer_status";
    public static final String NAME = "customer_name";
    public static final String ID = "C1";
    public static final String OTHER_ID = "C2";
    public static final String ORDER_DATE = "2024-01";
    public static final String STATUS_VALUE = "ACTIVE";
    public static final String CUSTOMER = "Ada";

    private AttributeNamingStrategyLocalChecks() {
    }

    public static CreateTableEnhancedRequest createTableRequest(ProvisionedThroughput throughput) {
        return CreateTableEnhancedRequest.builder()
            .provisionedThroughput(throughput)
            .globalSecondaryIndices(EnhancedGlobalSecondaryIndex.builder()
                .indexName(INDEX)
                .projection(projection -> projection.projectionType(ProjectionType.ALL))
                .provisionedThroughput(throughput)
                .build())
            .build();
    }

    public static Map<String, AttributeValue> primaryKey(String id) {
        Map<String, AttributeValue> key = new LinkedHashMap<>();
        key.put(PARTITION, AttributeValue.fromS(id));
        key.put(SORT, AttributeValue.fromS(ORDER_DATE));
        return key;
    }

    public static Map<String, AttributeValue> readItem(DynamoDbClient client, String tableName, String id) {
        return client.getItem(GetItemRequest.builder()
            .tableName(tableName)
            .key(primaryKey(id))
            .consistentRead(true)
            .build()).item();
    }

    public static void assertStoredNames(Map<String, AttributeValue> item) {
        assertThat(item.keySet()).contains(PARTITION, SORT, STATUS, NAME);
        assertThat(item).doesNotContainKey("customerName");
        assertThat(item).doesNotContainKey("orderDate");
        assertThat(item).doesNotContainKey("customerStatus");
        assertThat(item.get(NAME).s()).isEqualTo(CUSTOMER);
    }

    public static void assertKeySchema(DynamoDbClient client, String tableName) {
        TableDescription table =
            client.describeTable(DescribeTableRequest.builder().tableName(tableName).build()).table();
        assertThat(table.keySchema()).extracting(KeySchemaElement::attributeName).containsExactly(PARTITION, SORT);
        GlobalSecondaryIndexDescription index = table.globalSecondaryIndexes().get(0);
        assertThat(index.indexName()).isEqualTo(INDEX);
        assertThat(index.keySchema()).extracting(KeySchemaElement::attributeName).containsExactly(STATUS);
    }

    public static QueryEnhancedRequest sortQuery() {
        return QueryEnhancedRequest.builder()
            .queryConditional(QueryConditional.sortBeginsWith(key -> key.partitionValue(ID).sortValue("2024")))
            .build();
    }

    public static QueryEnhancedRequest indexQuery() {
        return QueryEnhancedRequest.builder()
            .queryConditional(QueryConditional.keyEqualTo(key -> key.partitionValue(STATUS_VALUE)))
            .build();
    }

    public static QueryEnhancedRequest filtered(String attributeName) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("#n", attributeName);
        Map<String, AttributeValue> values = new LinkedHashMap<>();
        values.put(":v", AttributeValue.fromS(CUSTOMER));
        Expression expression = Expression.builder()
            .expression("#n = :v")
            .expressionNames(names)
            .expressionValues(values)
            .build();
        return QueryEnhancedRequest.builder()
            .queryConditional(QueryConditional.keyEqualTo(key -> key.partitionValue(ID)))
            .filterExpression(expression)
            .build();
    }
}

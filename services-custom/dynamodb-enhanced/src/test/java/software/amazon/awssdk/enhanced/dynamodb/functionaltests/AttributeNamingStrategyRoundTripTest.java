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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static software.amazon.awssdk.enhanced.dynamodb.internal.AttributeValues.stringValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.BuiltInAttributeNamingStrategy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.BeanTableSchemaParams;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;

/**
 * Round trip through local DynamoDB with a LOWER_SNAKE_CASE bean schema, using the synchronous and asynchronous clients.
 */
public class AttributeNamingStrategyRoundTripTest extends LocalDynamoDbSyncTestBase {
    private static final TableSchema<CustomerOrder> TABLE_SCHEMA = TableSchema.fromBean(
        BeanTableSchemaParams.builder(CustomerOrder.class)
                             .attributeNamingStrategy(BuiltInAttributeNamingStrategy.LOWER_SNAKE_CASE)
                             .build());

    private static final Expression STATUS_EXISTS_BY_STORED_NAME =
        Expression.builder()
                  .expression("attribute_exists(#status)")
                  .putExpressionName("#status", "order_status")
                  .build();

    private static final Expression STATUS_EXISTS_BY_JAVA_NAME =
        Expression.builder()
                  .expression("attribute_exists(#status)")
                  .putExpressionName("#status", "orderStatus")
                  .build();

    private final DynamoDbAsyncClient asyncClient = localDynamoDb().createAsyncClient();

    private DynamoDbTable<CustomerOrder> syncTable;
    private DynamoDbAsyncTable<CustomerOrder> asyncTable;

    @Before
    public void createTable() {
        syncTable = DynamoDbEnhancedClient.builder()
                                          .dynamoDbClient(getDynamoDbClient())
                                          .build()
                                          .table(tableName(), TABLE_SCHEMA);
        asyncTable = DynamoDbEnhancedAsyncClient.builder()
                                                .dynamoDbClient(asyncClient)
                                                .build()
                                                .table(tableName(), TABLE_SCHEMA);
        syncTable.createTable(r -> r.provisionedThroughput(getDefaultProvisionedThroughput()));
    }

    @After
    public void deleteTable() {
        getDynamoDbClient().deleteTable(DeleteTableRequest.builder().tableName(tableName()).build());
        asyncClient.close();
    }

    @Test
    public void describeTable_whenSchemaUsesLowerSnakeCase_returnsStoredKeyNames() {
        assertThat(getDynamoDbClient().describeTable(r -> r.tableName(tableName())).table().keySchema())
            .extracting(keySchemaElement -> keySchemaElement.attributeName())
            .containsExactly("customer_id", "order_date");
    }

    @Test
    public void putItem_whenSyncClientUsesLowerSnakeCase_readsStoredNames() {
        CustomerOrder order = order("c-1", "2026-10-23", "OPEN");
        syncTable.putItem(order);

        assertThat(rawItem("c-1", "2026-10-23")).containsEntry("order_status", stringValue("OPEN"))
                                                .doesNotContainKey("orderStatus");
        assertThat(syncTable.getItem(key("c-1", "2026-10-23")).getOrderStatus()).isEqualTo("OPEN");
        assertThat(syncTable.query(QueryConditional.keyEqualTo(k -> k.partitionValue("c-1"))).items())
            .extracting(CustomerOrder::getOrderDate)
            .containsExactly("2026-10-23");
    }

    @Test
    public void putItem_whenSyncConditionUsesJavaName_fails() {
        syncTable.putItem(order("c-1", "2026-10-23", "OPEN"));

        syncTable.putItem(conditionalPut(order("c-1", "2026-10-23", "SHIPPED"), STATUS_EXISTS_BY_STORED_NAME));
        assertThat(syncTable.getItem(key("c-1", "2026-10-23")).getOrderStatus()).isEqualTo("SHIPPED");

        assertThatThrownBy(() -> syncTable.putItem(conditionalPut(order("c-1", "2026-10-23", "CLOSED"),
                                                                  STATUS_EXISTS_BY_JAVA_NAME)))
            .isInstanceOf(ConditionalCheckFailedException.class);
    }

    @Test
    public void putItem_whenAsyncClientUsesLowerSnakeCase_readsStoredNames() {
        asyncTable.putItem(order("c-2", "2026-10-24", "OPEN")).join();

        assertThat(rawItem("c-2", "2026-10-24")).containsEntry("order_status", stringValue("OPEN"));
        assertThat(asyncTable.getItem(key("c-2", "2026-10-24")).join().getOrderStatus()).isEqualTo("OPEN");

        List<CustomerOrder> queried = new ArrayList<>();
        asyncTable.query(QueryConditional.keyEqualTo(k -> k.partitionValue("c-2"))).items().subscribe(queried::add).join();
        assertThat(queried).extracting(CustomerOrder::getOrderDate).containsExactly("2026-10-24");
    }

    @Test
    public void putItem_whenAsyncConditionUsesJavaName_fails() {
        asyncTable.putItem(order("c-2", "2026-10-24", "OPEN")).join();

        asyncTable.putItem(conditionalPut(order("c-2", "2026-10-24", "SHIPPED"), STATUS_EXISTS_BY_STORED_NAME)).join();
        assertThat(asyncTable.getItem(key("c-2", "2026-10-24")).join().getOrderStatus()).isEqualTo("SHIPPED");

        assertThatThrownBy(() -> asyncTable.putItem(conditionalPut(order("c-2", "2026-10-24", "CLOSED"),
                                                                   STATUS_EXISTS_BY_JAVA_NAME)).join())
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(ConditionalCheckFailedException.class);
    }

    private String tableName() {
        return getConcreteTableName("naming-strategy-orders");
    }

    private Map<String, AttributeValue> rawItem(String customerId, String orderDate) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("customer_id", stringValue(customerId));
        key.put("order_date", stringValue(orderDate));
        return getDynamoDbClient().getItem(r -> r.tableName(tableName()).key(key)).item();
    }

    private static Key key(String customerId, String orderDate) {
        return Key.builder().partitionValue(customerId).sortValue(orderDate).build();
    }

    private static PutItemEnhancedRequest<CustomerOrder> conditionalPut(CustomerOrder order, Expression condition) {
        return PutItemEnhancedRequest.builder(CustomerOrder.class).item(order).conditionExpression(condition).build();
    }

    private static CustomerOrder order(String customerId, String orderDate, String orderStatus) {
        CustomerOrder order = new CustomerOrder();
        order.setCustomerId(customerId);
        order.setOrderDate(orderDate);
        order.setOrderStatus(orderStatus);
        return order;
    }

    @DynamoDbBean
    public static class CustomerOrder {
        private String customerId;
        private String orderDate;
        private String orderStatus;

        @DynamoDbPartitionKey
        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        @DynamoDbSortKey
        public String getOrderDate() {
            return orderDate;
        }

        public void setOrderDate(String orderDate) {
            this.orderDate = orderDate;
        }

        public String getOrderStatus() {
            return orderStatus;
        }

        public void setOrderStatus(String orderStatus) {
            this.orderStatus = orderStatus;
        }
    }
}

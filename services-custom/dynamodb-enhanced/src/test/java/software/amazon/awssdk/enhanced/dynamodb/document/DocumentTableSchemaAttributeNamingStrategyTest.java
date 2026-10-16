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

package software.amazon.awssdk.enhanced.dynamodb.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.mapper.AttributeNamingStrategies;
import software.amazon.awssdk.enhanced.dynamodb.mapper.AttributeNamingStrategy;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class DocumentTableSchemaAttributeNamingStrategyTest {
    private static final String PRIMARY = TableMetadata.primaryIndexName();

    @Test
    public void omittedStrategy_keepsDeclaredNames() {
        DocumentTableSchema schema = DocumentTableSchema.builder()
            .addIndexPartitionKey(PRIMARY, "customerId", AttributeValueType.S)
            .build();
        EnhancedDocument document = EnhancedDocument.builder().putString("customerId", "C1").build();

        assertThat(schema.tableMetadata().primaryPartitionKey()).isEqualTo("customerId");
        assertThat(schema.itemToMap(document, false)).containsKey("customerId");
    }

    @Test
    public void eachBuiltIn_transformsDeclaredKeysAndDocumentKeys() {
        assertStored(AttributeNamingStrategies.SNAKE_CASE, "customer_id", "http_request");
        assertStored(AttributeNamingStrategies.LOWER_CAMEL_CASE, "customerId", "httpRequest");
        assertStored(AttributeNamingStrategies.UPPER_CAMEL_CASE, "CustomerId", "HttpRequest");
        assertStored(AttributeNamingStrategies.IDENTITY, "customerId", "httpRequest");
    }

    @Test
    public void customStrategy_transformsDeclaredKeysAndDocumentKeys() {
        assertStored(name -> name + "X", "customerIdX", "httpRequestX");
    }

    @Test
    public void addStoredName_keepsTheDeclaredKey() {
        DocumentTableSchema schema = DocumentTableSchema.builder()
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .addStoredName("PK")
            .addIndexPartitionKey(PRIMARY, "PK", AttributeValueType.S)
            .addIndexSortKey(PRIMARY, "orderDate", AttributeValueType.S)
            .build();
        EnhancedDocument document = EnhancedDocument.builder()
            .putString("PK", "C1")
            .putString("orderDate", "2024-01")
            .build();

        assertThat(schema.tableMetadata().primaryPartitionKey()).isEqualTo("PK");
        assertThat(schema.tableMetadata().primarySortKey()).contains("order_date");
        assertThat(schema.attributeNames()).contains("PK", "order_date");
        assertThat(schema.itemToMap(document, false)).containsKeys("PK", "order_date");
    }

    @Test
    public void fromJson_transformsTopLevelKeysAndKeepsNestedKeys() {
        DocumentTableSchema schema = snakeSchema();
        EnhancedDocument document = EnhancedDocument.fromJson(
            "{\"customerId\":\"C1\",\"address\":{\"postalCode\":\"98101\"}}");

        Map<String, AttributeValue> item = schema.itemToMap(document, false);
        assertThat(item).containsKey("customer_id");
        assertThat(item.get("address").m()).containsOnlyKeys("postalCode");
    }

    @Test
    public void nullAndEmptyResults_areRejected() {
        EnhancedDocument document = EnhancedDocument.builder().putString("customerId", "C1").build();

        assertThatThrownBy(() -> schema(name -> null).itemToMap(document, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null")
            .hasMessageContaining("customerId");
        assertThatThrownBy(() -> schema(name -> "").itemToMap(document, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty")
            .hasMessageContaining("customerId");
    }

    @Test
    public void collidingDocumentKeys_areRejected() {
        DocumentTableSchema schema = snakeSchema();
        EnhancedDocument document = EnhancedDocument.builder()
            .putString("httpRequest", "GET")
            .putString("http_request", "POST")
            .build();

        assertThatThrownBy(() -> schema.itemToMap(document, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("httpRequest")
            .hasMessageContaining("http_request");
    }

    @Test
    public void loadedDocument_exposesTheStoredName() {
        DocumentTableSchema schema = snakeSchema();
        EnhancedDocument written = EnhancedDocument.builder().putString("customerId", "C1").build();
        EnhancedDocument loaded = schema.mapToItem(schema.itemToMap(written, false));

        assertThat(loaded.getString("customer_id")).isEqualTo("C1");
        assertThat(loaded.getString("customerId")).isNull();
    }

    @Test
    public void rewritingALoadedDocument_keepsTheStoredName() {
        AttributeNamingStrategy[] strategies = {
            AttributeNamingStrategies.SNAKE_CASE,
            AttributeNamingStrategies.LOWER_CAMEL_CASE,
            AttributeNamingStrategies.UPPER_CAMEL_CASE,
            AttributeNamingStrategies.IDENTITY
        };
        for (AttributeNamingStrategy strategy : strategies) {
            DocumentTableSchema schema = schema(strategy);
            EnhancedDocument written = EnhancedDocument.builder().putString("customerId", "C1").build();
            Map<String, AttributeValue> stored = schema.itemToMap(written, false);
            Map<String, AttributeValue> rewritten = schema.itemToMap(schema.mapToItem(stored), false);

            assertThat(rewritten.keySet()).isEqualTo(stored.keySet());
        }
    }

    @Test
    public void secondBuild_doesNotAddTheKeyAgain() {
        DocumentTableSchema.Builder builder = DocumentTableSchema.builder()
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .addIndexPartitionKey(PRIMARY, "customerId", AttributeValueType.S);

        assertThat(builder.build().tableMetadata().primaryPartitionKey()).isEqualTo("customer_id");
        assertThat(builder.build().tableMetadata().primaryPartitionKey()).isEqualTo("customer_id");
    }

    @Test
    public void duplicateDeclaredKey_isRejectedOnTheAddCall() {
        assertThatThrownBy(() -> DocumentTableSchema.builder()
            .addIndexPartitionKey(PRIMARY, "customerId", AttributeValueType.S)
            .addIndexPartitionKey(PRIMARY, "customerId", AttributeValueType.S))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("customerId")
            .hasMessageContaining(PRIMARY);
    }

    private static void assertStored(AttributeNamingStrategy strategy, String customerId, String httpRequest) {
        DocumentTableSchema schema = schema(strategy);
        EnhancedDocument document = EnhancedDocument.builder()
            .putString("customerId", "C1")
            .putString("httpRequest", "GET")
            .build();

        assertThat(schema.tableMetadata().primaryPartitionKey()).isEqualTo(customerId);
        assertThat(schema.attributeNames()).contains(customerId);
        assertThat(schema.itemToMap(document, false)).containsKeys(customerId, httpRequest);
    }

    private static DocumentTableSchema snakeSchema() {
        return schema(AttributeNamingStrategies.SNAKE_CASE);
    }

    private static DocumentTableSchema schema(AttributeNamingStrategy strategy) {
        return DocumentTableSchema.builder()
            .attributeNamingStrategy(strategy)
            .addIndexPartitionKey(PRIMARY, "customerId", AttributeValueType.S)
            .build();
    }
}

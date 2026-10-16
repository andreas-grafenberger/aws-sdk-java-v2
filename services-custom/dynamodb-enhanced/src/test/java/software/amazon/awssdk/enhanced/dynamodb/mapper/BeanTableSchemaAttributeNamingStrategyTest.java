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

package software.amazon.awssdk.enhanced.dynamodb.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbFlatten;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.testbeans.SimpleBean;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class BeanTableSchemaAttributeNamingStrategyTest {

    @Before
    @After
    public void clearCache() {
        BeanTableSchema.clearSchemaCache();
    }

    @Test
    public void omittedStrategy_keepsTodaysNames() {
        TableSchema<Customer> schema = schema(null);

        assertThat(schema.attributeNames()).contains("PK", "customerId", "httpRequest");
        assertThat(schema.itemToMap(customer(), false)).containsKeys("PK", "customerId");
    }

    @Test
    public void eachBuiltIn_transformsUnannotatedProperties() {
        assertThat(schema(AttributeNamingStrategies.SNAKE_CASE).attributeNames())
            .contains("PK", "customer_id", "http_request");
        assertThat(schema(AttributeNamingStrategies.LOWER_CAMEL_CASE).attributeNames())
            .contains("PK", "customerId", "httpRequest");
        assertThat(schema(AttributeNamingStrategies.UPPER_CAMEL_CASE).attributeNames())
            .contains("PK", "CustomerId", "HttpRequest");
        assertThat(schema(AttributeNamingStrategies.IDENTITY).attributeNames())
            .contains("PK", "customerId", "httpRequest");
    }

    @Test
    public void customStrategy_transformsUnannotatedProperties() {
        assertThat(schema(name -> name + "X").attributeNames()).contains("PK", "customerIdX");
    }

    @Test
    public void annotation_isStoredAsWrittenUnderEveryBuiltIn() {
        for (AttributeNamingStrategy strategy : new AttributeNamingStrategy[] {
            AttributeNamingStrategies.SNAKE_CASE,
            AttributeNamingStrategies.LOWER_CAMEL_CASE,
            AttributeNamingStrategies.UPPER_CAMEL_CASE,
            AttributeNamingStrategies.IDENTITY}) {
            assertThat(schema(strategy).tableMetadata().primaryPartitionKey()).isEqualTo("PK");
        }
    }

    @Test
    public void duplicateStoredName_namesBothInputsAndTheAnnotation() {
        assertThatThrownBy(() -> schema(AttributeNamingStrategies.SNAKE_CASE, Duplicate.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate")
            .hasMessageContaining("SNAKE_CASE")
            .hasMessageContaining("httpRequest")
            .hasMessageContaining("@DynamoDbAttribute")
            .hasMessageContaining("http_request");
    }

    @Test
    public void nullAndEmptyResults_areRejected() {
        assertThatThrownBy(() -> schema(name -> null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null")
            .hasMessageContaining("customerId")
            .hasMessageContaining("Customer");
        assertThatThrownBy(() -> schema(name -> ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty")
            .hasMessageContaining("customerId");
    }

    @Test
    public void placeholderClash_namesBothStoredNames() {
        assertThatThrownBy(() -> schema(name -> {
            if ("orderId".equals(name)) {
                return "order-id";
            }
            if ("orderRef".equals(name)) {
                return "order.id";
            }
            return name;
        }, Placeholder.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("order-id")
            .hasMessageContaining("order.id")
            .hasMessageContaining("#AMZN_MAPPED_order_id");
    }

    @Test
    public void discoveredNestedClass_usesTheParentStrategy() {
        TableSchema<Order> schema = schema(AttributeNamingStrategies.SNAKE_CASE, Order.class);
        Order order = new Order();
        order.setId("1");
        Address address = new Address();
        address.setPostalCode("98101");
        order.setAddress(address);

        Map<String, AttributeValue> item = schema.itemToMap(order, false);
        assertThat(item.get("address").m()).containsOnlyKeys("postal_code");
    }

    @Test
    public void discoveredFlattenedClass_usesTheParentStrategy() {
        TableSchema<FlattenedOrder> schema = schema(AttributeNamingStrategies.SNAKE_CASE, FlattenedOrder.class);

        assertThat(schema.attributeNames()).contains("created_at").doesNotContain("createdAt", "audit");
    }

    @Test
    public void flattenedMap_isRejectedWhenTheStrategyChangesNames() {
        assertThatThrownBy(() -> schema(AttributeNamingStrategies.SNAKE_CASE, FlattenedMapBean.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("labels");
        assertThat(schema(AttributeNamingStrategies.IDENTITY, FlattenedMapBean.class).attributeNames()).contains("id");
        assertThat(schema(null, FlattenedMapBean.class).attributeNames()).contains("id");
    }

    @Test
    public void cache_separatesOmittedIdentityAndSnakeCase() {
        TableSchema<SimpleBean> omitted = TableSchema.fromBean(BeanTableSchemaParams.builder(SimpleBean.class).build());
        TableSchema<SimpleBean> omittedAgain =
            TableSchema.fromBean(BeanTableSchemaParams.builder(SimpleBean.class).build());
        TableSchema<SimpleBean> snake = schema(AttributeNamingStrategies.SNAKE_CASE, SimpleBean.class);
        TableSchema<SimpleBean> snakeAgain = schema(AttributeNamingStrategies.SNAKE_CASE, SimpleBean.class);
        TableSchema<SimpleBean> identity = schema(AttributeNamingStrategies.IDENTITY, SimpleBean.class);
        TableSchema<SimpleBean> identityAgain = schema(AttributeNamingStrategies.IDENTITY, SimpleBean.class);

        assertThat(omitted).isSameAs(omittedAgain);
        assertThat(snake).isSameAs(snakeAgain);
        assertThat(identity).isSameAs(identityAgain);
        assertThat(omitted).isNotSameAs(snake);
        assertThat(omitted).isNotSameAs(identity);
        assertThat(snake.attributeNames()).contains("integer_attribute");
        assertThat(omitted.attributeNames()).contains("integerAttribute");
    }

    @Test
    public void customStrategy_isNotCached() {
        AttributeNamingStrategy strategy = name -> name;
        TableSchema<SimpleBean> first = schema(strategy, SimpleBean.class);
        TableSchema<SimpleBean> second = schema(strategy, SimpleBean.class);

        assertThat(first).isNotSameAs(second);
    }

    @Test
    public void fromClass_doesNotApplyAStrategy() {
        assertThat(TableSchema.fromClass(Customer.class).attributeNames()).contains("customerId").doesNotContain("customer_id");
    }

    @Test
    public void selfReference_buildsUnderAStrategy() {
        TableSchema<SelfRef> schema = schema(AttributeNamingStrategies.SNAKE_CASE, SelfRef.class);
        SelfRef child = new SelfRef();
        child.setId("child");
        child.setGivenName("Ada");
        SelfRef parent = new SelfRef();
        parent.setId("parent");
        parent.setGivenName("Grace");
        parent.setChild(child);

        Map<String, AttributeValue> item = schema.itemToMap(parent, false);
        assertThat(item).containsKey("given_name");
        assertThat(item.get("child").m()).containsKey("given_name");
    }

    @Test
    public void customStrategy_buildsASelfReference() {
        TableSchema<SelfRef> schema = schema(name -> name.toUpperCase(java.util.Locale.ROOT), SelfRef.class);

        assertThat(schema.attributeNames()).contains("ID", "GIVENNAME", "CHILD");
    }

    private static TableSchema<Customer> schema(AttributeNamingStrategy strategy) {
        return schema(strategy, Customer.class);
    }

    private static <T> TableSchema<T> schema(AttributeNamingStrategy strategy, Class<T> itemClass) {
        return TableSchema.fromBean(BeanTableSchemaParams.builder(itemClass)
            .attributeNamingStrategy(strategy)
            .build());
    }

    private static Customer customer() {
        Customer customer = new Customer();
        customer.setId("C1");
        customer.setCustomerId("C1");
        customer.setHttpRequest("GET");
        return customer;
    }

    @DynamoDbBean
    public static class Customer {
        private String id;
        private String customerId;
        private String httpRequest;

        @DynamoDbPartitionKey
        @DynamoDbAttribute("PK")
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public String getHttpRequest() {
            return httpRequest;
        }

        public void setHttpRequest(String httpRequest) {
            this.httpRequest = httpRequest;
        }
    }

    @DynamoDbBean
    public static class Duplicate {
        private String id;
        private String httpRequest;
        private String legacyId;

        @DynamoDbPartitionKey
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getHttpRequest() {
            return httpRequest;
        }

        public void setHttpRequest(String httpRequest) {
            this.httpRequest = httpRequest;
        }

        @DynamoDbAttribute("http_request")
        public String getLegacyId() {
            return legacyId;
        }

        public void setLegacyId(String legacyId) {
            this.legacyId = legacyId;
        }
    }

    @DynamoDbBean
    public static class Placeholder {
        private String id;
        private String orderId;
        private String orderRef;

        @DynamoDbPartitionKey
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public String getOrderRef() {
            return orderRef;
        }

        public void setOrderRef(String orderRef) {
            this.orderRef = orderRef;
        }
    }

    @DynamoDbBean
    public static class Order {
        private String id;
        private Address address;

        @DynamoDbPartitionKey
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }
    }

    @DynamoDbBean
    public static class Address {
        private String postalCode;

        public String getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }
    }

    @DynamoDbBean
    public static class FlattenedOrder {
        private String id;
        private Audit audit;

        @DynamoDbPartitionKey
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @DynamoDbFlatten
        public Audit getAudit() {
            return audit;
        }

        public void setAudit(Audit audit) {
            this.audit = audit;
        }
    }

    @DynamoDbBean
    public static class Audit {
        private String createdAt;

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    @DynamoDbBean
    public static class FlattenedMapBean {
        private String id;
        private Map<String, String> labels;

        @DynamoDbPartitionKey
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @DynamoDbFlatten
        public Map<String, String> getLabels() {
            return labels;
        }

        public void setLabels(Map<String, String> labels) {
            this.labels = labels;
        }
    }

    @DynamoDbBean
    public static class SelfRef {
        private String id;
        private String givenName;
        private SelfRef child;

        @DynamoDbPartitionKey
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getGivenName() {
            return givenName;
        }

        public void setGivenName(String givenName) {
            this.givenName = givenName;
        }

        public SelfRef getChild() {
            return child;
        }

        public void setChild(SelfRef child) {
            this.child = child;
        }
    }
}

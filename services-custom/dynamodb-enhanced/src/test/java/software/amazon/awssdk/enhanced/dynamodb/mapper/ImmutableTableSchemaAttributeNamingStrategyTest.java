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

import java.util.Locale;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbFlatten;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class ImmutableTableSchemaAttributeNamingStrategyTest {

    @Before
    @After
    public void clearCache() {
        ImmutableTableSchema.clearSchemaCache();
    }

    @Test
    public void omittedStrategy_keepsTodaysNames() {
        assertThat(schema(null).attributeNames()).contains("PK", "customerId", "httpRequest");
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
        AttributeNamingStrategy[] strategies = {
            AttributeNamingStrategies.SNAKE_CASE,
            AttributeNamingStrategies.LOWER_CAMEL_CASE,
            AttributeNamingStrategies.UPPER_CAMEL_CASE,
            AttributeNamingStrategies.IDENTITY
        };
        for (AttributeNamingStrategy strategy : strategies) {
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
        Order order = Order.builder()
            .id("1")
            .address(Address.builder().postalCode("98101").build())
            .build();

        Map<String, AttributeValue> item = schema.itemToMap(order, false);
        assertThat(item.get("address").m()).containsOnlyKeys("postal_code");
    }

    @Test
    public void discoveredFlattenedClass_usesTheParentStrategy() {
        TableSchema<FlattenedOrder> schema = schema(AttributeNamingStrategies.SNAKE_CASE, FlattenedOrder.class);

        assertThat(schema.attributeNames()).contains("created_at").doesNotContain("createdAt", "audit");
    }

    @Test
    public void cache_separatesOmittedIdentityAndSnakeCase() {
        TableSchema<Customer> omitted = schema(null);
        TableSchema<Customer> omittedAgain = schema(null);
        TableSchema<Customer> snake = schema(AttributeNamingStrategies.SNAKE_CASE);
        TableSchema<Customer> snakeAgain = schema(AttributeNamingStrategies.SNAKE_CASE);
        TableSchema<Customer> identity = schema(AttributeNamingStrategies.IDENTITY);
        TableSchema<Customer> identityAgain = schema(AttributeNamingStrategies.IDENTITY);

        assertThat(omitted).isSameAs(omittedAgain);
        assertThat(snake).isSameAs(snakeAgain);
        assertThat(identity).isSameAs(identityAgain);
        assertThat(omitted).isNotSameAs(snake);
        assertThat(omitted).isNotSameAs(identity);
    }

    @Test
    public void customStrategy_isNotCached() {
        AttributeNamingStrategy strategy = name -> name;
        assertThat(schema(strategy)).isNotSameAs(schema(strategy));
    }

    @Test
    public void fromClass_doesNotApplyAStrategy() {
        assertThat(TableSchema.fromClass(Customer.class).attributeNames())
            .contains("customerId")
            .doesNotContain("customer_id");
    }

    @Test
    public void selfReference_buildsUnderAStrategy() {
        TableSchema<SelfRef> schema = schema(AttributeNamingStrategies.SNAKE_CASE, SelfRef.class);
        SelfRef child = SelfRef.builder().id("child").givenName("Ada").build();
        SelfRef parent = SelfRef.builder().id("parent").givenName("Grace").child(child).build();

        Map<String, AttributeValue> item = schema.itemToMap(parent, false);
        assertThat(item).containsKey("given_name");
        assertThat(item.get("child").m()).containsKey("given_name");
    }

    @Test
    public void customStrategy_buildsASelfReference() {
        TableSchema<SelfRef> schema = schema(name -> name.toUpperCase(Locale.ROOT), SelfRef.class);

        assertThat(schema.attributeNames()).contains("ID", "GIVENNAME", "CHILD");
    }

    private static TableSchema<Customer> schema(AttributeNamingStrategy strategy) {
        return schema(strategy, Customer.class);
    }

    private static <T> TableSchema<T> schema(AttributeNamingStrategy strategy, Class<T> itemClass) {
        return TableSchema.fromImmutableClass(ImmutableTableSchemaParams.builder(itemClass)
            .attributeNamingStrategy(strategy)
            .build());
    }

    @DynamoDbImmutable(builder = Customer.Builder.class)
    public static class Customer {
        private final String id;
        private final String customerId;
        private final String httpRequest;

        private Customer(Builder builder) {
            this.id = builder.id;
            this.customerId = builder.customerId;
            this.httpRequest = builder.httpRequest;
        }

        public static Builder builder() {
            return new Builder();
        }

        @DynamoDbPartitionKey
        @DynamoDbAttribute("PK")
        public String id() {
            return id;
        }

        public String customerId() {
            return customerId;
        }

        public String httpRequest() {
            return httpRequest;
        }

        public static final class Builder {
            private String id;
            private String customerId;
            private String httpRequest;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder customerId(String customerId) {
                this.customerId = customerId;
                return this;
            }

            public Builder httpRequest(String httpRequest) {
                this.httpRequest = httpRequest;
                return this;
            }

            public Customer build() {
                return new Customer(this);
            }
        }
    }

    @DynamoDbImmutable(builder = Duplicate.Builder.class)
    public static class Duplicate {
        private final String id;
        private final String httpRequest;
        private final String legacyId;

        private Duplicate(Builder builder) {
            this.id = builder.id;
            this.httpRequest = builder.httpRequest;
            this.legacyId = builder.legacyId;
        }

        public static Builder builder() {
            return new Builder();
        }

        @DynamoDbPartitionKey
        public String id() {
            return id;
        }

        public String httpRequest() {
            return httpRequest;
        }

        @DynamoDbAttribute("http_request")
        public String legacyId() {
            return legacyId;
        }

        public static final class Builder {
            private String id;
            private String httpRequest;
            private String legacyId;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder httpRequest(String httpRequest) {
                this.httpRequest = httpRequest;
                return this;
            }

            public Builder legacyId(String legacyId) {
                this.legacyId = legacyId;
                return this;
            }

            public Duplicate build() {
                return new Duplicate(this);
            }
        }
    }

    @DynamoDbImmutable(builder = Placeholder.Builder.class)
    public static class Placeholder {
        private final String id;
        private final String orderId;
        private final String orderRef;

        private Placeholder(Builder builder) {
            this.id = builder.id;
            this.orderId = builder.orderId;
            this.orderRef = builder.orderRef;
        }

        public static Builder builder() {
            return new Builder();
        }

        @DynamoDbPartitionKey
        public String id() {
            return id;
        }

        public String orderId() {
            return orderId;
        }

        public String orderRef() {
            return orderRef;
        }

        public static final class Builder {
            private String id;
            private String orderId;
            private String orderRef;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder orderId(String orderId) {
                this.orderId = orderId;
                return this;
            }

            public Builder orderRef(String orderRef) {
                this.orderRef = orderRef;
                return this;
            }

            public Placeholder build() {
                return new Placeholder(this);
            }
        }
    }

    @DynamoDbImmutable(builder = Order.Builder.class)
    public static class Order {
        private final String id;
        private final Address address;

        private Order(Builder builder) {
            this.id = builder.id;
            this.address = builder.address;
        }

        public static Builder builder() {
            return new Builder();
        }

        @DynamoDbPartitionKey
        public String id() {
            return id;
        }

        public Address address() {
            return address;
        }

        public static final class Builder {
            private String id;
            private Address address;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder address(Address address) {
                this.address = address;
                return this;
            }

            public Order build() {
                return new Order(this);
            }
        }
    }

    @DynamoDbImmutable(builder = Address.Builder.class)
    public static class Address {
        private final String postalCode;

        private Address(Builder builder) {
            this.postalCode = builder.postalCode;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String postalCode() {
            return postalCode;
        }

        public static final class Builder {
            private String postalCode;

            public Builder postalCode(String postalCode) {
                this.postalCode = postalCode;
                return this;
            }

            public Address build() {
                return new Address(this);
            }
        }
    }

    @DynamoDbImmutable(builder = FlattenedOrder.Builder.class)
    public static class FlattenedOrder {
        private final String id;
        private final Audit audit;

        private FlattenedOrder(Builder builder) {
            this.id = builder.id;
            this.audit = builder.audit;
        }

        public static Builder builder() {
            return new Builder();
        }

        @DynamoDbPartitionKey
        public String id() {
            return id;
        }

        @DynamoDbFlatten
        public Audit audit() {
            return audit;
        }

        public static final class Builder {
            private String id;
            private Audit audit;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder audit(Audit audit) {
                this.audit = audit;
                return this;
            }

            public FlattenedOrder build() {
                return new FlattenedOrder(this);
            }
        }
    }

    @DynamoDbImmutable(builder = Audit.Builder.class)
    public static class Audit {
        private final String createdAt;

        private Audit(Builder builder) {
            this.createdAt = builder.createdAt;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String createdAt() {
            return createdAt;
        }

        public static final class Builder {
            private String createdAt;

            public Builder createdAt(String createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public Audit build() {
                return new Audit(this);
            }
        }
    }

    @DynamoDbImmutable(builder = SelfRef.Builder.class)
    public static class SelfRef {
        private final String id;
        private final String givenName;
        private final SelfRef child;

        private SelfRef(Builder builder) {
            this.id = builder.id;
            this.givenName = builder.givenName;
            this.child = builder.child;
        }

        public static Builder builder() {
            return new Builder();
        }

        @DynamoDbPartitionKey
        public String id() {
            return id;
        }

        public String givenName() {
            return givenName;
        }

        public SelfRef child() {
            return child;
        }

        public static final class Builder {
            private String id;
            private String givenName;
            private SelfRef child;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder givenName(String givenName) {
                this.givenName = givenName;
                return this;
            }

            public Builder child(SelfRef child) {
                this.child = child;
                return this;
            }

            public SelfRef build() {
                return new SelfRef(this);
            }
        }
    }
}

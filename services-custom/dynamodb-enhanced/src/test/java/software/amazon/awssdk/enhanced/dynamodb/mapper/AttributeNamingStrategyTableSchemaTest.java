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
import static software.amazon.awssdk.enhanced.dynamodb.internal.AttributeValues.stringValue;
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.primaryPartitionKey;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbFlatten;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class AttributeNamingStrategyTableSchemaTest {
    private static final AttributeNamingStrategy LOWER_SNAKE_CASE = BuiltInAttributeNamingStrategy.LOWER_SNAKE_CASE;

    @Test
    void attributeNames_whenBeanUsesLowerSnakeCase_storesSnakeCaseAndAnnotatedName() {
        TableSchema<BeanCustomer> schema = TableSchema.fromBean(
            BeanTableSchemaParams.builder(BeanCustomer.class)
                                 .attributeNamingStrategy(LOWER_SNAKE_CASE)
                                 .build());

        assertThat(schema.attributeNames()).containsExactlyInAnyOrder("customer_id", "PK");
        assertThat(schema.tableMetadata().primaryPartitionKey()).isEqualTo("PK");
    }

    @Test
    void attributeNames_whenImmutableUsesLowerSnakeCase_storesSnakeCaseAndAnnotatedName() {
        TableSchema<ImmutableCustomer> schema = TableSchema.fromImmutableClass(
            ImmutableTableSchemaParams.builder(ImmutableCustomer.class)
                                      .attributeNamingStrategy(LOWER_SNAKE_CASE)
                                      .build());

        assertThat(schema.attributeNames()).containsExactlyInAnyOrder("customer_id", "PK");
        assertThat(schema.tableMetadata().primaryPartitionKey()).isEqualTo("PK");
    }

    @Test
    void attributeNames_whenStaticSchemaUsesLowerSnakeCase_transformsEveryDeclaredName() {
        TableSchema<MutableCustomer> schema = staticCustomerSchema();

        assertThat(schema.attributeNames()).containsExactlyInAnyOrder("customer_id", "pk");
        assertThat(schema.tableMetadata().primaryPartitionKey()).isEqualTo("pk");
    }

    @Test
    void attributeNames_whenStaticImmutableUsesLowerSnakeCase_transformsEveryDeclaredName() {
        TableSchema<ImmutableRecord> schema = StaticImmutableTableSchema
            .builder(ImmutableRecord.class, ImmutableRecord.Builder.class)
            .newItemBuilder(ImmutableRecord::builder, ImmutableRecord.Builder::build)
            .attributeNamingStrategy(LOWER_SNAKE_CASE)
            .addAttribute(String.class, attribute -> attribute.name("customerId")
                                                              .getter(ImmutableRecord::customerId)
                                                              .setter(ImmutableRecord.Builder::customerId))
            .addAttribute(String.class, attribute -> attribute.name("PK")
                                                              .getter(ImmutableRecord::id)
                                                              .setter(ImmutableRecord.Builder::id)
                                                              .tags(primaryPartitionKey()))
            .build();

        assertThat(schema.attributeNames()).containsExactlyInAnyOrder("customer_id", "pk");
        assertThat(schema.tableMetadata().primaryPartitionKey()).isEqualTo("pk");
    }

    @Test
    void attributeNames_whenStrategyIsOmitted_keepsJavaPropertyName() {
        TableSchema<BeanCustomer> schema = TableSchema.fromBean(BeanCustomer.class);

        assertThat(schema.attributeNames()).containsExactlyInAnyOrder("customerId", "PK");
    }

    @Test
    void itemToMap_whenNestedBeanInheritsStrategy_storesResolvedChildNames() {
        TableSchema<Order> schema = TableSchema.fromBean(
            BeanTableSchemaParams.builder(Order.class)
                                 .attributeNamingStrategy(LOWER_SNAKE_CASE)
                                 .build());

        Order order = new Order();
        order.setOrderId("o-1");
        Address address = new Address();
        address.setStreetName("Main");
        order.setAddress(address);

        Map<String, AttributeValue> mapped = schema.itemToMap(order, true);
        assertThat(mapped).containsOnlyKeys("order_id", "address")
                          .containsEntry("order_id", stringValue("o-1"));
        assertThat(mapped.get("address").m()).containsOnlyKeys("street_name")
                                              .containsEntry("street_name", stringValue("Main"));
    }

    @Test
    void itemToMap_whenFlattenedChildInheritsStrategy_storesResolvedNamesOnParent() {
        TableSchema<CustomerWithContact> schema = TableSchema.fromBean(
            BeanTableSchemaParams.builder(CustomerWithContact.class)
                                 .attributeNamingStrategy(LOWER_SNAKE_CASE)
                                 .build());

        CustomerWithContact customer = new CustomerWithContact();
        customer.setCustomerId("c-1");
        Contact contact = new Contact();
        contact.setPhoneNumber("555");
        customer.setContact(contact);

        Map<String, AttributeValue> mapped = schema.itemToMap(customer, true);
        assertThat(mapped).containsOnlyKeys("customer_id", "phone_number")
                          .containsEntry("customer_id", stringValue("c-1"))
                          .containsEntry("phone_number", stringValue("555"));
    }

    @Test
    void attributeNames_whenSuppliedChildHasItsOwnNames_keepsThoseNames() {
        TableSchema<SuppliedChild> childSchema = StaticTableSchema.builder(SuppliedChild.class)
            .newItemSupplier(SuppliedChild::new)
            .addAttribute(String.class, attribute -> attribute.name("givenName")
                                                              .getter(SuppliedChild::getGivenName)
                                                              .setter(SuppliedChild::setGivenName))
            .build();

        TableSchema<ParentWithSuppliedChild> schema = StaticTableSchema.builder(ParentWithSuppliedChild.class)
            .newItemSupplier(ParentWithSuppliedChild::new)
            .attributeNamingStrategy(LOWER_SNAKE_CASE)
            .addAttribute(String.class, attribute -> attribute.name("customerId")
                                                              .getter(ParentWithSuppliedChild::getCustomerId)
                                                              .setter(ParentWithSuppliedChild::setCustomerId))
            .flatten(childSchema, ParentWithSuppliedChild::getChild, ParentWithSuppliedChild::setChild)
            .build();

        assertThat(schema.attributeNames()).containsExactlyInAnyOrder("customer_id", "givenName");
    }

    @Test
    void attributeNames_whenCustomStrategyLowercases_storesLowercaseAndAnnotatedName() {
        TableSchema<BeanCustomer> schema = TableSchema.fromBean(
            BeanTableSchemaParams.builder(BeanCustomer.class)
                                 .attributeNamingStrategy(new LowercaseAttributeNamingStrategy())
                                 .build());

        assertThat(schema.attributeNames()).containsExactlyInAnyOrder("customerid", "PK");
    }

    @Test
    void itemToMap_whenBeanUsesLowerSnakeCase_readsStoredNames() {
        TableSchema<BeanCustomer> schema = TableSchema.fromBean(
            BeanTableSchemaParams.builder(BeanCustomer.class)
                                 .attributeNamingStrategy(LOWER_SNAKE_CASE)
                                 .build());

        BeanCustomer customer = new BeanCustomer();
        customer.setCustomerId("c-1");
        customer.setId("pk-1");

        Map<String, AttributeValue> mapped = schema.itemToMap(customer, true);
        assertThat(mapped).containsOnlyKeys("customer_id", "PK")
                          .containsEntry("customer_id", stringValue("c-1"))
                          .containsEntry("PK", stringValue("pk-1"));

        BeanCustomer read = schema.mapToItem(mapped);
        assertThat(read.getCustomerId()).isEqualTo("c-1");
        assertThat(read.getId()).isEqualTo("pk-1");
    }

    @Test
    void itemToMap_whenStaticSchemaUsesLowerSnakeCase_readsStoredNames() {
        TableSchema<MutableCustomer> schema = staticCustomerSchema();

        MutableCustomer customer = new MutableCustomer();
        customer.setCustomerId("c-1");
        customer.setId("pk-1");

        Map<String, AttributeValue> mapped = schema.itemToMap(customer, true);
        assertThat(mapped).containsOnlyKeys("customer_id", "pk")
                          .containsEntry("customer_id", stringValue("c-1"))
                          .containsEntry("pk", stringValue("pk-1"));

        MutableCustomer read = schema.mapToItem(mapped);
        assertThat(read.getCustomerId()).isEqualTo("c-1");
        assertThat(read.getId()).isEqualTo("pk-1");
    }

    private static TableSchema<MutableCustomer> staticCustomerSchema() {
        return StaticTableSchema.builder(MutableCustomer.class)
                                .newItemSupplier(MutableCustomer::new)
                                .attributeNamingStrategy(LOWER_SNAKE_CASE)
                                .addAttribute(String.class, attribute -> attribute.name("customerId")
                                                                                  .getter(MutableCustomer::getCustomerId)
                                                                                  .setter(MutableCustomer::setCustomerId))
                                .addAttribute(String.class, attribute -> attribute.name("PK")
                                                                                  .getter(MutableCustomer::getId)
                                                                                  .setter(MutableCustomer::setId)
                                                                                  .tags(primaryPartitionKey()))
                                .build();
    }

    /**
     * Lowercases the whole source name and does not split words.
     * {@code customerId} is stored as {@code customerid}.
     */
    private static final class LowercaseAttributeNamingStrategy implements AttributeNamingStrategy {
        @Override
        public String transform(String sourceName) {
            return sourceName.toLowerCase(Locale.ROOT);
        }
    }

    @DynamoDbBean
    public static class BeanCustomer {
        private String customerId;
        private String id;

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        @DynamoDbPartitionKey
        @DynamoDbAttribute("PK")
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    @DynamoDbImmutable(builder = ImmutableCustomer.Builder.class)
    public static final class ImmutableCustomer {
        private final String customerId;
        private final String id;

        private ImmutableCustomer(Builder builder) {
            this.customerId = builder.customerId;
            this.id = builder.id;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String customerId() {
            return customerId;
        }

        @DynamoDbPartitionKey
        @DynamoDbAttribute("PK")
        public String id() {
            return id;
        }

        public static final class Builder {
            private String customerId;
            private String id;

            public Builder customerId(String customerId) {
                this.customerId = customerId;
                return this;
            }

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public ImmutableCustomer build() {
                return new ImmutableCustomer(this);
            }
        }
    }

    @DynamoDbBean
    public static class Order {
        private String orderId;
        private Address address;

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
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
        private String streetName;

        public String getStreetName() {
            return streetName;
        }

        public void setStreetName(String streetName) {
            this.streetName = streetName;
        }
    }

    @DynamoDbBean
    public static class CustomerWithContact {
        private String customerId;
        private Contact contact;

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        @DynamoDbFlatten
        public Contact getContact() {
            return contact;
        }

        public void setContact(Contact contact) {
            this.contact = contact;
        }
    }

    @DynamoDbBean
    public static class Contact {
        private String phoneNumber;

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }
    }

    public static class MutableCustomer {
        private String customerId;
        private String id;

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static final class ImmutableRecord {
        private final String customerId;
        private final String id;

        private ImmutableRecord(Builder builder) {
            this.customerId = builder.customerId;
            this.id = builder.id;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String customerId() {
            return customerId;
        }

        public String id() {
            return id;
        }

        public static final class Builder {
            private String customerId;
            private String id;

            public Builder customerId(String customerId) {
                this.customerId = customerId;
                return this;
            }

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public ImmutableRecord build() {
                return new ImmutableRecord(this);
            }
        }
    }

    public static class ParentWithSuppliedChild {
        private String customerId;
        private SuppliedChild child;

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public SuppliedChild getChild() {
            return child;
        }

        public void setChild(SuppliedChild child) {
            this.child = child;
        }
    }

    public static class SuppliedChild {
        private String givenName;

        public String getGivenName() {
            return givenName;
        }

        public void setGivenName(String givenName) {
            this.givenName = givenName;
        }
    }
}

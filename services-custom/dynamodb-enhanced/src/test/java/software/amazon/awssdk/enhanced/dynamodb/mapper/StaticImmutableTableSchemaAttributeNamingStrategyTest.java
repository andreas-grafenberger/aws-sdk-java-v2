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
import static software.amazon.awssdk.enhanced.dynamodb.extensions.VersionedRecordExtension.AttributeTags.versionAttribute;
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.primaryPartitionKey;
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.primarySortKey;
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.secondaryPartitionKey;

import java.util.Map;
import org.junit.Test;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class StaticImmutableTableSchemaAttributeNamingStrategyTest {

    @Test
    public void omittedStrategy_keepsDeclaredNames() {
        assertThat(schema(null).attributeNames()).contains("customerId", "PK");
    }

    @Test
    public void snakeCase_transformsNamesAndMetadata() {
        TableSchema<Item> schema = schema(AttributeNamingStrategies.SNAKE_CASE);

        assertThat(schema.attributeNames()).contains("customer_id", "PK", "pk", "order_date", "customer_status");
        assertThat(schema.tableMetadata().primaryPartitionKey()).isEqualTo("PK");
        assertThat(schema.tableMetadata().primarySortKey()).contains("order_date");
        assertThat(schema.tableMetadata().indexPartitionKey("byStatus")).isEqualTo("customer_status");
        assertThat(schema.tableMetadata().customMetadataObject(
            "VersionedRecordExtension:VersionAttribute", String.class)).contains("record_version");
    }

    @Test
    public void eachBuiltInAndCustomStrategy_transformThePlainAttribute() {
        assertThat(plainName(AttributeNamingStrategies.LOWER_CAMEL_CASE)).isEqualTo("customerId");
        assertThat(plainName(AttributeNamingStrategies.UPPER_CAMEL_CASE)).isEqualTo("CustomerId");
        assertThat(plainName(AttributeNamingStrategies.IDENTITY)).isEqualTo("customerId");
        assertThat(plainName(name -> name + "X")).isEqualTo("customerIdX");
    }

    @Test
    public void strategySetAfterAddAttribute_stillTransforms() {
        TableSchema<Item> schema = StaticImmutableTableSchema.builder(Item.class, Item.Builder.class)
            .newItemBuilder(Item::builder, Item.Builder::build)
            .addAttribute(String.class, a -> a.name("customerId")
                .getter(Item::customerId)
                .setter(Item.Builder::customerId))
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .build();

        assertThat(schema.attributeNames()).containsExactly("customer_id");
    }

    @Test
    public void duplicateStoredName_nullResult_andEmptyResult_areRejected() {
        assertThatThrownBy(this::duplicateSchema)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SNAKE_CASE")
            .hasMessageContaining("httpRequest")
            .hasMessageContaining("http_request");
        assertThatThrownBy(() -> plainName(name -> null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null")
            .hasMessageContaining("customerId");
        assertThatThrownBy(() -> plainName(name -> ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    public void placeholderClash_namesBothStoredNames() {
        assertThatThrownBy(() -> StaticImmutableTableSchema.builder(Item.class, Item.Builder.class)
            .newItemBuilder(Item::builder, Item.Builder::build)
            .attributeNamingStrategy((AttributeNamingStrategy) name -> {
                if ("orderId".equals(name)) {
                    return "order-id";
                }
                if ("orderRef".equals(name)) {
                    return "order.id";
                }
                return name;
            })
            .addAttribute(String.class, a -> a.name("orderId").getter(Item::customerId).setter(Item.Builder::customerId))
            .addAttribute(String.class, a -> a.name("orderRef").getter(Item::orderDate).setter(Item.Builder::orderDate))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("order-id")
            .hasMessageContaining("order.id");
    }

    @Test
    public void nameAndStoredNameTogether_areRejected() {
        assertThatThrownBy(() -> StaticImmutableTableSchema.builder(Item.class, Item.Builder.class)
            .newItemBuilder(Item::builder, Item.Builder::build)
            .addAttribute(String.class, a -> a.name("PK").storedName("PK")
                .getter(Item::id)
                .setter(Item.Builder::id))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("storedName")
            .hasMessageContaining("PK");
    }

    @Test
    public void flattenedMap_isRejectedOnlyWhenTheStrategyChangesNames() {
        assertThatThrownBy(() -> mapSchema(AttributeNamingStrategies.SNAKE_CASE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("labels");
        assertThat(mapSchema(AttributeNamingStrategies.IDENTITY).attributeNames()).contains("customerId");
        assertThat(mapSchema(null).attributeNames()).contains("customerId");
    }

    @Test
    public void prebuiltChildren_followTheParentStrategyRule() {
        TableSchema<Child> omitted = childSchema(null);
        TableSchema<Child> identity = childSchema(AttributeNamingStrategies.IDENTITY);

        assertThatThrownBy(() -> flatten(AttributeNamingStrategies.SNAKE_CASE, omitted))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Child")
            .hasMessageContaining("SNAKE_CASE");
        assertThat(flatten(AttributeNamingStrategies.SNAKE_CASE, identity).attributeNames())
            .contains("postalCode", "customer_name");
        assertThat(flatten(AttributeNamingStrategies.IDENTITY, omitted).attributeNames())
            .contains("postalCode", "customerName");
        assertThat(flatten(null, omitted).attributeNames()).contains("postalCode");

        assertThatThrownBy(() -> document(AttributeNamingStrategies.SNAKE_CASE, omitted))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Child");
        Parent parent = Parent.builder()
            .customerName("Ada")
            .child(Child.builder().postalCode("98101").build())
            .build();
        Map<String, AttributeValue> item =
            document(AttributeNamingStrategies.SNAKE_CASE, identity).itemToMap(parent, false);
        assertThat(item.get("address").m()).containsOnlyKeys("postalCode");
    }

    @Test
    public void extendedSchema_keepsNamesOnlyWhenItDeclaresAStrategy() {
        StaticImmutableTableSchema<Item, Item.Builder> omittedSuper = superSchema(null);
        assertThatThrownBy(() -> extend(AttributeNamingStrategies.SNAKE_CASE, omittedSuper))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SNAKE_CASE");

        TableSchema<Item> schema = extend(AttributeNamingStrategies.SNAKE_CASE,
                                           superSchema(AttributeNamingStrategies.IDENTITY));
        assertThat(schema.attributeNames()).contains("customerId", "order_date");
    }

    private void duplicateSchema() {
        StaticImmutableTableSchema.builder(Item.class, Item.Builder.class)
            .newItemBuilder(Item::builder, Item.Builder::build)
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .addAttribute(String.class, a -> a.name("httpRequest").getter(Item::customerId).setter(Item.Builder::customerId))
            .addAttribute(String.class, a -> a.name("http_request").getter(Item::orderDate).setter(Item.Builder::orderDate))
            .build();
    }

    private static String plainName(AttributeNamingStrategy strategy) {
        return StaticImmutableTableSchema.builder(Item.class, Item.Builder.class)
            .newItemBuilder(Item::builder, Item.Builder::build)
            .attributeNamingStrategy(strategy)
            .addAttribute(String.class, a -> a.name("customerId").getter(Item::customerId).setter(Item.Builder::customerId))
            .build()
            .attributeNames()
            .get(0);
    }

    private static TableSchema<Item> schema(AttributeNamingStrategy strategy) {
        StaticImmutableTableSchema.Builder<Item, Item.Builder> builder =
            StaticImmutableTableSchema.builder(Item.class, Item.Builder.class)
                .newItemBuilder(Item::builder, Item.Builder::build)
                .addAttribute(String.class, a -> a.storedName("PK")
                    .getter(Item::id).setter(Item.Builder::id).tags(primaryPartitionKey()))
                .addAttribute(String.class, a -> a.name("customerId")
                    .getter(Item::customerId).setter(Item.Builder::customerId))
                .addAttribute(String.class, a -> a.name("orderDate")
                    .getter(Item::orderDate).setter(Item.Builder::orderDate).tags(primarySortKey()))
                .addAttribute(String.class, a -> a.name("customerStatus")
                    .getter(Item::customerStatus).setter(Item.Builder::customerStatus)
                    .tags(secondaryPartitionKey("byStatus")))
                .addAttribute(Integer.class, a -> a.name("recordVersion")
                    .getter(Item::recordVersion).setter(Item.Builder::recordVersion).tags(versionAttribute()));
        if (strategy != null) {
            builder.attributeNamingStrategy(strategy);
            builder.addAttribute(String.class, a -> a.name("PK")
                .getter(Item::pkInput).setter(Item.Builder::pkInput));
        }
        return builder.build();
    }

    private static TableSchema<Item> mapSchema(AttributeNamingStrategy strategy) {
        StaticImmutableTableSchema.Builder<Item, Item.Builder> builder =
            StaticImmutableTableSchema.builder(Item.class, Item.Builder.class)
                .newItemBuilder(Item::builder, Item.Builder::build)
                .flatten("labels", Item::labels, Item.Builder::labels)
                .addAttribute(String.class, a -> a.name("customerId")
                    .getter(Item::customerId).setter(Item.Builder::customerId));
        if (strategy != null) {
            builder.attributeNamingStrategy(strategy);
        }
        return builder.build();
    }

    private static TableSchema<Child> childSchema(AttributeNamingStrategy strategy) {
        StaticImmutableTableSchema.Builder<Child, Child.Builder> builder =
            StaticImmutableTableSchema.builder(Child.class, Child.Builder.class)
                .newItemBuilder(Child::builder, Child.Builder::build)
                .addAttribute(String.class, a -> a.name("postalCode")
                    .getter(Child::postalCode).setter(Child.Builder::postalCode));
        if (strategy != null) {
            builder.attributeNamingStrategy(strategy);
        }
        return builder.build();
    }

    private static TableSchema<Parent> flatten(AttributeNamingStrategy strategy, TableSchema<Child> child) {
        StaticImmutableTableSchema.Builder<Parent, Parent.Builder> builder =
            StaticImmutableTableSchema.builder(Parent.class, Parent.Builder.class)
                .newItemBuilder(Parent::builder, Parent.Builder::build)
                .addAttribute(String.class, a -> a.name("customerName")
                    .getter(Parent::customerName).setter(Parent.Builder::customerName))
                .flatten(child, Parent::child, Parent.Builder::child);
        if (strategy != null) {
            builder.attributeNamingStrategy(strategy);
        }
        return builder.build();
    }

    private static TableSchema<Parent> document(AttributeNamingStrategy strategy, TableSchema<Child> child) {
        StaticImmutableTableSchema.Builder<Parent, Parent.Builder> builder =
            StaticImmutableTableSchema.builder(Parent.class, Parent.Builder.class)
                .newItemBuilder(Parent::builder, Parent.Builder::build)
                .addAttribute(String.class, a -> a.name("customerName")
                    .getter(Parent::customerName).setter(Parent.Builder::customerName))
                .addAttribute(EnhancedType.documentOf(Child.class, child), a -> a.name("address")
                    .getter(Parent::child).setter(Parent.Builder::child));
        if (strategy != null) {
            builder.attributeNamingStrategy(strategy);
        }
        return builder.build();
    }

    private static StaticImmutableTableSchema<Item, Item.Builder> superSchema(AttributeNamingStrategy strategy) {
        StaticImmutableTableSchema.Builder<Item, Item.Builder> builder =
            StaticImmutableTableSchema.builder(Item.class, Item.Builder.class)
                .newItemBuilder(Item::builder, Item.Builder::build)
                .addAttribute(String.class, a -> a.name("customerId")
                    .getter(Item::customerId).setter(Item.Builder::customerId));
        if (strategy != null) {
            builder.attributeNamingStrategy(strategy);
        }
        return builder.build();
    }

    private static TableSchema<Item> extend(AttributeNamingStrategy strategy,
                                            StaticImmutableTableSchema<Item, Item.Builder> superSchema) {
        StaticImmutableTableSchema.Builder<Item, Item.Builder> builder =
            StaticImmutableTableSchema.builder(Item.class, Item.Builder.class)
                .newItemBuilder(Item::builder, Item.Builder::build)
                .extend(superSchema)
                .addAttribute(String.class, a -> a.name("orderDate")
                    .getter(Item::orderDate).setter(Item.Builder::orderDate));
        if (strategy != null) {
            builder.attributeNamingStrategy(strategy);
        }
        return builder.build();
    }

    private static final class Item {
        private final String id;
        private final String pkInput;
        private final String customerId;
        private final String orderDate;
        private final String customerStatus;
        private final Integer recordVersion;
        private final Map<String, String> labels;

        private Item(Builder builder) {
            this.id = builder.id;
            this.pkInput = builder.pkInput;
            this.customerId = builder.customerId;
            this.orderDate = builder.orderDate;
            this.customerStatus = builder.customerStatus;
            this.recordVersion = builder.recordVersion;
            this.labels = builder.labels;
        }

        private static Builder builder() {
            return new Builder();
        }

        private String id() {
            return id;
        }

        private String pkInput() {
            return pkInput;
        }

        private String customerId() {
            return customerId;
        }

        private String orderDate() {
            return orderDate;
        }

        private String customerStatus() {
            return customerStatus;
        }

        private Integer recordVersion() {
            return recordVersion;
        }

        private Map<String, String> labels() {
            return labels;
        }

        private static final class Builder {
            private String id;
            private String pkInput;
            private String customerId;
            private String orderDate;
            private String customerStatus;
            private Integer recordVersion;
            private Map<String, String> labels;

            private Builder id(String id) {
                this.id = id;
                return this;
            }

            private Builder pkInput(String pkInput) {
                this.pkInput = pkInput;
                return this;
            }

            private Builder customerId(String customerId) {
                this.customerId = customerId;
                return this;
            }

            private Builder orderDate(String orderDate) {
                this.orderDate = orderDate;
                return this;
            }

            private Builder customerStatus(String customerStatus) {
                this.customerStatus = customerStatus;
                return this;
            }

            private Builder recordVersion(Integer recordVersion) {
                this.recordVersion = recordVersion;
                return this;
            }

            private Builder labels(Map<String, String> labels) {
                this.labels = labels;
                return this;
            }

            private Item build() {
                return new Item(this);
            }
        }
    }

    private static final class Parent {
        private final String customerName;
        private final Child child;

        private Parent(Builder builder) {
            this.customerName = builder.customerName;
            this.child = builder.child;
        }

        private static Builder builder() {
            return new Builder();
        }

        private String customerName() {
            return customerName;
        }

        private Child child() {
            return child;
        }

        private static final class Builder {
            private String customerName;
            private Child child;

            private Builder customerName(String customerName) {
                this.customerName = customerName;
                return this;
            }

            private Builder child(Child child) {
                this.child = child;
                return this;
            }

            private Parent build() {
                return new Parent(this);
            }
        }
    }

    private static final class Child {
        private final String postalCode;

        private Child(Builder builder) {
            this.postalCode = builder.postalCode;
        }

        private static Builder builder() {
            return new Builder();
        }

        private String postalCode() {
            return postalCode;
        }

        private static final class Builder {
            private String postalCode;

            private Builder postalCode(String postalCode) {
                this.postalCode = postalCode;
                return this;
            }

            private Child build() {
                return new Child(this);
            }
        }
    }
}

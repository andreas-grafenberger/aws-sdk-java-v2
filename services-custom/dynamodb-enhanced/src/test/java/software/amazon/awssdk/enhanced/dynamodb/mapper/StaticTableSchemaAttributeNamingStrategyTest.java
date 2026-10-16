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
import software.amazon.awssdk.enhanced.dynamodb.document.DocumentTableSchema;
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class StaticTableSchemaAttributeNamingStrategyTest {

    @Test
    public void omittedStrategy_keepsDeclaredNames() {
        TableSchema<Item> schema = schema(null);

        assertThat(schema.attributeNames()).contains("customerId", "PK");
        assertThat(schema.itemToMap(item(), false)).containsKey("customerId");
    }

    @Test
    public void snakeCase_transformsNamesAndKeepsStoredName() {
        TableSchema<Item> schema = schema(AttributeNamingStrategies.SNAKE_CASE);

        assertThat(schema.attributeNames()).contains("customer_id", "PK", "pk", "order_date", "customer_status");
        assertThat(schema.attributeNames()).doesNotContain("customerId");
        assertThat(schema.tableMetadata().primaryPartitionKey()).isEqualTo("PK");
        assertThat(schema.tableMetadata().primarySortKey()).contains("order_date");
        assertThat(schema.tableMetadata().indexPartitionKey("byStatus")).isEqualTo("customer_status");
        assertThat(schema.tableMetadata().customMetadataObject(
            "VersionedRecordExtension:VersionAttribute", String.class)).contains("record_version");
        assertThat(schema.itemToMap(item(), false)).containsKeys("customer_id", "PK");
    }

    @Test
    public void eachBuiltIn_transformsThePlainAttribute() {
        assertThat(storedPlainName(AttributeNamingStrategies.LOWER_CAMEL_CASE)).isEqualTo("customerId");
        assertThat(storedPlainName(AttributeNamingStrategies.UPPER_CAMEL_CASE)).isEqualTo("CustomerId");
        assertThat(storedPlainName(AttributeNamingStrategies.IDENTITY)).isEqualTo("customerId");
        assertThat(schema(AttributeNamingStrategies.SNAKE_CASE).attributeNames()).contains("pk");
    }

    @Test
    public void namePk_isTransformedWhileStoredNameIsNot() {
        TableSchema<Item> schema = schema(AttributeNamingStrategies.SNAKE_CASE);

        assertThat(schema.attributeNames()).contains("PK", "pk");
    }

    @Test
    public void strategySetAfterAddAttribute_stillTransforms() {
        TableSchema<Item> schema = StaticTableSchema.builder(Item.class)
            .newItemSupplier(Item::new)
            .addAttribute(String.class, a -> a.name("customerId")
                .getter(Item::getCustomerId)
                .setter(Item::setCustomerId))
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .build();

        assertThat(schema.attributeNames()).containsExactly("customer_id");
    }

    @Test
    public void customStrategy_transformsTheName() {
        TableSchema<Item> schema = schema(name -> name + "X");

        assertThat(schema.attributeNames()).contains("customerIdX");
        assertThat(schema.attributeNames()).contains("PK");
    }

    @Test
    public void duplicateStoredName_namesBothInputs() {
        assertThatThrownBy(() -> StaticTableSchema.builder(Item.class)
            .newItemSupplier(Item::new)
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .addAttribute(String.class, a -> a.name("httpRequest")
                .getter(Item::getCustomerId)
                .setter(Item::setCustomerId))
            .addAttribute(String.class, a -> a.name("http_request")
                .getter(Item::getOrderDate)
                .setter(Item::setOrderDate))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Item")
            .hasMessageContaining("SNAKE_CASE")
            .hasMessageContaining("httpRequest")
            .hasMessageContaining("http_request");
    }

    @Test
    public void nullResult_isRejected() {
        assertThatThrownBy(() -> schema(name -> null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null")
            .hasMessageContaining("customerId")
            .hasMessageContaining("Item");
    }

    @Test
    public void emptyResult_isRejected() {
        assertThatThrownBy(() -> schema(name -> ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty")
            .hasMessageContaining("customerId");
    }

    @Test
    public void placeholderClash_namesBothStoredNames() {
        assertThatThrownBy(() -> StaticTableSchema.builder(Item.class)
            .newItemSupplier(Item::new)
            .attributeNamingStrategy((AttributeNamingStrategy) name -> {
                if ("orderId".equals(name)) {
                    return "order-id";
                }
                if ("orderRef".equals(name)) {
                    return "order.id";
                }
                return name;
            })
            .addAttribute(String.class, a -> a.name("orderId")
                .getter(Item::getCustomerId)
                .setter(Item::setCustomerId))
            .addAttribute(String.class, a -> a.name("orderRef")
                .getter(Item::getOrderDate)
                .setter(Item::setOrderDate))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("order-id")
            .hasMessageContaining("order.id")
            .hasMessageContaining("#AMZN_MAPPED_order_id");
    }

    @Test
    public void nameAndStoredNameTogether_areRejected() {
        assertThatThrownBy(() -> StaticTableSchema.builder(Item.class)
            .newItemSupplier(Item::new)
            .addAttribute(String.class, a -> a.name("PK").storedName("PK")
                .getter(Item::getId)
                .setter(Item::setId))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name")
            .hasMessageContaining("storedName")
            .hasMessageContaining("PK");
    }

    @Test
    public void flattenedMap_isRejectedWhenTheStrategyChangesNames() {
        assertThatThrownBy(() -> StaticTableSchema.builder(Item.class)
            .newItemSupplier(Item::new)
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .flatten("labels", Item::getLabels, Item::setLabels)
            .addAttribute(String.class, a -> a.name("customerId")
                .getter(Item::getCustomerId)
                .setter(Item::setCustomerId))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("labels");
    }

    @Test
    public void flattenedMap_isAcceptedUnderIdentity() {
        TableSchema<Item> schema = StaticTableSchema.builder(Item.class)
            .newItemSupplier(Item::new)
            .attributeNamingStrategy(AttributeNamingStrategies.IDENTITY)
            .flatten("labels", Item::getLabels, Item::setLabels)
            .addAttribute(String.class, a -> a.name("customerId")
                .getter(Item::getCustomerId)
                .setter(Item::setCustomerId))
            .build();

        assertThat(schema.attributeNames()).contains("customerId");
    }

    @Test
    public void prebuiltChildWithoutStrategy_isRejectedUnderSnakeCase() {
        TableSchema<Child> child = childSchema(null);

        assertThatThrownBy(() -> parentWithFlattenedChild(AttributeNamingStrategies.SNAKE_CASE, child))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Child")
            .hasMessageContaining("SNAKE_CASE");
    }

    @Test
    public void prebuiltChildWithItsOwnStrategy_keepsItsNames() {
        TableSchema<Child> child = childSchema(AttributeNamingStrategies.IDENTITY);
        TableSchema<Parent> schema = parentWithFlattenedChild(AttributeNamingStrategies.SNAKE_CASE, child);

        assertThat(schema.attributeNames()).contains("postalCode", "customer_name");
    }

    @Test
    public void prebuiltChildWithoutStrategy_isAcceptedUnderIdentity() {
        TableSchema<Parent> schema = parentWithFlattenedChild(AttributeNamingStrategies.IDENTITY, childSchema(null));

        assertThat(schema.attributeNames()).contains("postalCode", "customerName");
    }

    @Test
    public void prebuiltChildWithoutStrategy_isAcceptedWhenTheParentOmitsTheStrategy() {
        TableSchema<Parent> schema = parentWithFlattenedChild(null, childSchema(null));

        assertThat(schema.attributeNames()).contains("postalCode", "customerName");
    }

    @Test
    public void documentOfChildWithoutStrategy_isRejectedUnderSnakeCase() {
        assertThatThrownBy(() -> parentWithDocumentChild(AttributeNamingStrategies.SNAKE_CASE, childSchema(null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Child");
    }

    @Test
    public void documentOfChildWithIdentity_keepsTheChildNames() {
        TableSchema<Parent> schema =
            parentWithDocumentChild(AttributeNamingStrategies.SNAKE_CASE, childSchema(AttributeNamingStrategies.IDENTITY));

        Parent parent = new Parent();
        parent.setCustomerName("Ada");
        Child child = new Child();
        child.setPostalCode("98101");
        parent.setChild(child);
        Map<String, AttributeValue> item = schema.itemToMap(parent, false);
        assertThat(item).containsKey("customer_name");
        assertThat(item.get("address").m()).containsOnlyKeys("postalCode");
    }

    @Test
    public void extendedSchemaWithoutStrategy_isRejectedUnderSnakeCase() {
        StaticTableSchema<Item> superSchema = StaticTableSchema.builder(Item.class)
            .newItemSupplier(Item::new)
            .addAttribute(String.class, a -> a.name("customerId")
                .getter(Item::getCustomerId)
                .setter(Item::setCustomerId))
            .build();

        assertThatThrownBy(() -> StaticTableSchema.builder(Item.class)
            .newItemSupplier(Item::new)
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .extend(superSchema)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SNAKE_CASE");
    }

    @Test
    public void extendedSchemaWithIdentity_keepsTheSuperNames() {
        StaticTableSchema<Item> superSchema = StaticTableSchema.builder(Item.class)
            .newItemSupplier(Item::new)
            .attributeNamingStrategy(AttributeNamingStrategies.IDENTITY)
            .addAttribute(String.class, a -> a.name("customerId")
                .getter(Item::getCustomerId)
                .setter(Item::setCustomerId))
            .build();

        TableSchema<Item> schema = StaticTableSchema.builder(Item.class)
            .newItemSupplier(Item::new)
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .extend(superSchema)
            .addAttribute(String.class, a -> a.name("orderDate")
                .getter(Item::getOrderDate)
                .setter(Item::setOrderDate))
            .build();

        assertThat(schema.attributeNames()).contains("customerId", "order_date");
    }

    @Test
    public void unknownChildSchema_isRejectedWhenTheParentChangesNames() {
        assertThatThrownBy(() -> StaticTableSchema.builder(Parent.class)
            .newItemSupplier(Parent::new)
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .addAttribute(String.class, a -> a.name("customerName")
                .getter(Parent::getCustomerName)
                .setter(Parent::setCustomerName))
            .flatten(DocumentTableSchema.builder().build(),
                     (Parent parent) -> (EnhancedDocument) null,
                     (Parent parent, EnhancedDocument document) ->
                         parent.setCustomerName(document == null ? "x" : "y"))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SNAKE_CASE");
    }

    private static String storedPlainName(AttributeNamingStrategy strategy) {
        TableSchema<Item> schema = StaticTableSchema.builder(Item.class)
            .newItemSupplier(Item::new)
            .attributeNamingStrategy(strategy)
            .addAttribute(String.class, a -> a.name("customerId")
                .getter(Item::getCustomerId)
                .setter(Item::setCustomerId))
            .build();
        return schema.attributeNames().get(0);
    }

    private static TableSchema<Item> schema(AttributeNamingStrategy strategy) {
        StaticTableSchema.Builder<Item> builder = StaticTableSchema.builder(Item.class)
            .newItemSupplier(Item::new)
            .addAttribute(String.class, a -> a.storedName("PK")
                .getter(Item::getId)
                .setter(Item::setId)
                .tags(primaryPartitionKey()))
            .addAttribute(String.class, a -> a.name("customerId")
                .getter(Item::getCustomerId)
                .setter(Item::setCustomerId))
            .addAttribute(String.class, a -> a.name("orderDate")
                .getter(Item::getOrderDate)
                .setter(Item::setOrderDate)
                .tags(primarySortKey()))
            .addAttribute(String.class, a -> a.name("customerStatus")
                .getter(Item::getCustomerStatus)
                .setter(Item::setCustomerStatus)
                .tags(secondaryPartitionKey("byStatus")))
            .addAttribute(Integer.class, a -> a.name("recordVersion")
                .getter(Item::getRecordVersion)
                .setter(Item::setRecordVersion)
                .tags(versionAttribute()));
        if (strategy != null) {
            builder.attributeNamingStrategy(strategy);
            builder.addAttribute(String.class, a -> a.name("PK")
                .getter(Item::getPkInput)
                .setter(Item::setPkInput));
        }
        return builder.build();
    }

    private static TableSchema<Child> childSchema(AttributeNamingStrategy strategy) {
        StaticTableSchema.Builder<Child> builder = StaticTableSchema.builder(Child.class)
            .newItemSupplier(Child::new)
            .addAttribute(String.class, a -> a.name("postalCode")
                .getter(Child::getPostalCode)
                .setter(Child::setPostalCode));
        if (strategy != null) {
            builder.attributeNamingStrategy(strategy);
        }
        return builder.build();
    }

    private static TableSchema<Parent> parentWithFlattenedChild(AttributeNamingStrategy strategy,
                                                                TableSchema<Child> child) {
        StaticTableSchema.Builder<Parent> builder = StaticTableSchema.builder(Parent.class)
            .newItemSupplier(Parent::new)
            .addAttribute(String.class, a -> a.name("customerName")
                .getter(Parent::getCustomerName)
                .setter(Parent::setCustomerName))
            .flatten(child, Parent::getChild, Parent::setChild);
        if (strategy != null) {
            builder.attributeNamingStrategy(strategy);
        }
        return builder.build();
    }

    private static TableSchema<Parent> parentWithDocumentChild(AttributeNamingStrategy strategy,
                                                               TableSchema<Child> child) {
        StaticTableSchema.Builder<Parent> builder = StaticTableSchema.builder(Parent.class)
            .newItemSupplier(Parent::new)
            .addAttribute(String.class, a -> a.name("customerName")
                .getter(Parent::getCustomerName)
                .setter(Parent::setCustomerName))
            .addAttribute(EnhancedType.documentOf(Child.class, child), a -> a.name("address")
                .getter(Parent::getChild)
                .setter(Parent::setChild));
        if (strategy != null) {
            builder.attributeNamingStrategy(strategy);
        }
        return builder.build();
    }

    private static Item item() {
        Item item = new Item();
        item.setId("C1");
        item.setCustomerId("C1");
        item.setOrderDate("2024-01-01");
        item.setCustomerStatus("ACTIVE");
        item.setRecordVersion(1);
        return item;
    }

    private static final class Item {
        private String id;
        private String pkInput;
        private String customerId;
        private String orderDate;
        private String customerStatus;
        private Integer recordVersion;
        private Map<String, String> labels;

        private String getId() {
            return id;
        }

        private void setId(String id) {
            this.id = id;
        }

        private String getPkInput() {
            return pkInput;
        }

        private void setPkInput(String pkInput) {
            this.pkInput = pkInput;
        }

        private String getCustomerId() {
            return customerId;
        }

        private void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        private String getOrderDate() {
            return orderDate;
        }

        private void setOrderDate(String orderDate) {
            this.orderDate = orderDate;
        }

        private String getCustomerStatus() {
            return customerStatus;
        }

        private void setCustomerStatus(String customerStatus) {
            this.customerStatus = customerStatus;
        }

        private Integer getRecordVersion() {
            return recordVersion;
        }

        private void setRecordVersion(Integer recordVersion) {
            this.recordVersion = recordVersion;
        }

        private Map<String, String> getLabels() {
            return labels;
        }

        private void setLabels(Map<String, String> labels) {
            this.labels = labels;
        }
    }

    private static final class Parent {
        private String customerName;
        private Child child;

        private String getCustomerName() {
            return customerName;
        }

        private void setCustomerName(String customerName) {
            this.customerName = customerName;
        }

        private Child getChild() {
            return child;
        }

        private void setChild(Child child) {
            this.child = child;
        }
    }

    private static final class Child {
        private String postalCode;

        private String getPostalCode() {
            return postalCode;
        }

        private void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }
    }
}

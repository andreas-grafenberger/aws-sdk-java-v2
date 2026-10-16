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
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.primaryPartitionKey;
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.primarySortKey;
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.secondaryPartitionKey;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.document.DocumentTableSchema;
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument;
import software.amazon.awssdk.enhanced.dynamodb.mapper.testbeans.NamingRegressionBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.testbeans.NamingRegressionImmutable;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Locks the attribute names produced before an attribute naming strategy existed.
 * The expected names are literals taken from that behavior. A schema that omits the strategy must keep them.
 */
public class OmittedAttributeNamingStrategyRegressionTest {
    private static final List<String> BEAN_ATTRIBUTE_NAMES = Arrays.asList(
        "PK", "orderDate", "customerStatus", "customerName", "address", "createdAt");

    @Test
    public void fromClassKeepsTodaysBeanNames() {
        TableSchema<NamingRegressionBean> schema = TableSchema.fromClass(NamingRegressionBean.class);

        assertTodaysKeys(schema);
        assertThat(schema.attributeNames()).containsExactlyInAnyOrderElementsOf(BEAN_ATTRIBUTE_NAMES);
        assertThat(schema.itemToMap(bean(), false).keySet())
            .containsExactlyInAnyOrderElementsOf(BEAN_ATTRIBUTE_NAMES);
        assertThat(schema.itemToMap(bean(), false).get("address").m()).containsOnlyKeys("postalCode");
    }

    @Test
    public void beanParamsWithoutStrategyKeepTodaysNames() {
        TableSchema<NamingRegressionBean> schema =
            TableSchema.fromBean(BeanTableSchemaParams.builder(NamingRegressionBean.class).build());

        assertTodaysKeys(schema);
        assertThat(schema.attributeNames()).containsExactlyInAnyOrderElementsOf(BEAN_ATTRIBUTE_NAMES);
        Map<String, AttributeValue> item = schema.itemToMap(bean(), false);
        assertThat(item.keySet()).containsExactlyInAnyOrderElementsOf(BEAN_ATTRIBUTE_NAMES);
        assertThat(item.get("address").m()).containsOnlyKeys("postalCode");
    }

    @Test
    public void fromClassKeepsTodaysImmutableNames() {
        TableSchema<NamingRegressionImmutable> schema = TableSchema.fromClass(NamingRegressionImmutable.class);

        assertTodaysKeys(schema);
        assertThat(schema.attributeNames()).containsExactlyInAnyOrderElementsOf(BEAN_ATTRIBUTE_NAMES);
        assertThat(schema.itemToMap(immutable(), false).get("address").m()).containsOnlyKeys("postalCode");
    }

    @Test
    public void immutableParamsWithoutStrategyKeepTodaysNames() {
        TableSchema<NamingRegressionImmutable> schema = TableSchema.fromImmutableClass(
            ImmutableTableSchemaParams.builder(NamingRegressionImmutable.class).build());

        assertTodaysKeys(schema);
        assertThat(schema.attributeNames()).containsExactlyInAnyOrderElementsOf(BEAN_ATTRIBUTE_NAMES);
        assertThat(schema.itemToMap(immutable(), false).keySet())
            .containsExactlyInAnyOrderElementsOf(BEAN_ATTRIBUTE_NAMES);
    }

    @Test
    public void staticSchemaWithoutStrategyKeepsDeclaredNames() {
        TableSchema<StaticItem> schema = staticSchema();

        assertTodaysKeys(schema);
        assertThat(schema.attributeNames()).containsExactly("PK", "orderDate", "customerStatus", "customerName");
        assertThat(schema.itemToMap(staticItem(), false).keySet())
            .containsExactlyInAnyOrder("PK", "orderDate", "customerStatus", "customerName");
    }

    @Test
    public void staticImmutableSchemaWithoutStrategyKeepsDeclaredNames() {
        TableSchema<StaticImmutableItem> schema = staticImmutableSchema();

        assertTodaysKeys(schema);
        assertThat(schema.attributeNames())
            .containsExactly("PK", "orderDate", "customerStatus", "customerName");
        assertThat(schema.itemToMap(staticImmutableItem(), false).keySet())
            .containsExactlyInAnyOrder("PK", "orderDate", "customerStatus", "customerName");
    }

    @Test
    public void documentSchemaWithoutStrategyKeepsDeclaredNames() {
        TableSchema<EnhancedDocument> schema = DocumentTableSchema.builder()
            .addIndexPartitionKey(TableMetadata.primaryIndexName(), "PK", AttributeValueType.S)
            .addIndexSortKey(TableMetadata.primaryIndexName(), "orderDate", AttributeValueType.S)
            .addIndexPartitionKey("byStatus", "customerStatus", AttributeValueType.S)
            .build();

        assertTodaysKeys(schema);
        assertThat(schema.attributeNames()).containsExactly("PK", "orderDate", "customerStatus");

        EnhancedDocument document = EnhancedDocument.builder()
            .putString("PK", "C1")
            .putString("orderDate", "2024-01-01")
            .putString("customerStatus", "ACTIVE")
            .putString("customerName", "Ada")
            .build();
        assertThat(schema.itemToMap(document, false).keySet())
            .containsExactlyInAnyOrder("PK", "orderDate", "customerStatus", "customerName");
    }

    private static void assertTodaysKeys(TableSchema<?> schema) {
        assertThat(schema.tableMetadata().primaryPartitionKey()).isEqualTo("PK");
        assertThat(schema.tableMetadata().primarySortKey()).contains("orderDate");
        assertThat(schema.tableMetadata().indexPartitionKey("byStatus")).isEqualTo("customerStatus");
    }

    private static NamingRegressionBean bean() {
        NamingRegressionBean.Address address = new NamingRegressionBean.Address();
        address.setPostalCode("98101");
        NamingRegressionBean.Audit audit = new NamingRegressionBean.Audit();
        audit.setCreatedAt("2024-01-01");
        NamingRegressionBean bean = new NamingRegressionBean();
        bean.setId("C1");
        bean.setOrderDate("2024-01-01");
        bean.setCustomerStatus("ACTIVE");
        bean.setCustomerName("Ada");
        bean.setAddress(address);
        bean.setAudit(audit);
        return bean;
    }

    private static NamingRegressionImmutable immutable() {
        return NamingRegressionImmutable.builder()
            .id("C1")
            .orderDate("2024-01-01")
            .customerStatus("ACTIVE")
            .customerName("Ada")
            .address(NamingRegressionImmutable.Address.builder().postalCode("98101").build())
            .audit(NamingRegressionImmutable.Audit.builder().createdAt("2024-01-01").build())
            .build();
    }

    private static TableSchema<StaticItem> staticSchema() {
        return StaticTableSchema.builder(StaticItem.class)
            .newItemSupplier(StaticItem::new)
            .addAttribute(String.class, a -> a.name("PK")
                .getter(StaticItem::getId)
                .setter(StaticItem::setId)
                .tags(primaryPartitionKey()))
            .addAttribute(String.class, a -> a.name("orderDate")
                .getter(StaticItem::getOrderDate)
                .setter(StaticItem::setOrderDate)
                .tags(primarySortKey()))
            .addAttribute(String.class, a -> a.name("customerStatus")
                .getter(StaticItem::getCustomerStatus)
                .setter(StaticItem::setCustomerStatus)
                .tags(secondaryPartitionKey("byStatus")))
            .addAttribute(String.class, a -> a.name("customerName")
                .getter(StaticItem::getCustomerName)
                .setter(StaticItem::setCustomerName))
            .build();
    }

    private static TableSchema<StaticImmutableItem> staticImmutableSchema() {
        return StaticImmutableTableSchema.builder(StaticImmutableItem.class, StaticImmutableItem.Builder.class)
            .newItemBuilder(StaticImmutableItem::builder, StaticImmutableItem.Builder::build)
            .addAttribute(String.class, a -> a.name("PK")
                .getter(StaticImmutableItem::id)
                .setter(StaticImmutableItem.Builder::id)
                .tags(primaryPartitionKey()))
            .addAttribute(String.class, a -> a.name("orderDate")
                .getter(StaticImmutableItem::orderDate)
                .setter(StaticImmutableItem.Builder::orderDate)
                .tags(primarySortKey()))
            .addAttribute(String.class, a -> a.name("customerStatus")
                .getter(StaticImmutableItem::customerStatus)
                .setter(StaticImmutableItem.Builder::customerStatus)
                .tags(secondaryPartitionKey("byStatus")))
            .addAttribute(String.class, a -> a.name("customerName")
                .getter(StaticImmutableItem::customerName)
                .setter(StaticImmutableItem.Builder::customerName))
            .build();
    }

    private static StaticItem staticItem() {
        StaticItem item = new StaticItem();
        item.setId("C1");
        item.setOrderDate("2024-01-01");
        item.setCustomerStatus("ACTIVE");
        item.setCustomerName("Ada");
        return item;
    }

    private static StaticImmutableItem staticImmutableItem() {
        return StaticImmutableItem.builder()
            .id("C1")
            .orderDate("2024-01-01")
            .customerStatus("ACTIVE")
            .customerName("Ada")
            .build();
    }

    private static final class StaticItem {
        private String id;
        private String orderDate;
        private String customerStatus;
        private String customerName;

        private String getId() {
            return id;
        }

        private void setId(String id) {
            this.id = id;
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

        private String getCustomerName() {
            return customerName;
        }

        private void setCustomerName(String customerName) {
            this.customerName = customerName;
        }
    }

    private static final class StaticImmutableItem {
        private final String id;
        private final String orderDate;
        private final String customerStatus;
        private final String customerName;

        private StaticImmutableItem(Builder builder) {
            this.id = builder.id;
            this.orderDate = builder.orderDate;
            this.customerStatus = builder.customerStatus;
            this.customerName = builder.customerName;
        }

        private static Builder builder() {
            return new Builder();
        }

        private String id() {
            return id;
        }

        private String orderDate() {
            return orderDate;
        }

        private String customerStatus() {
            return customerStatus;
        }

        private String customerName() {
            return customerName;
        }

        private static final class Builder {
            private String id;
            private String orderDate;
            private String customerStatus;
            private String customerName;

            private Builder id(String id) {
                this.id = id;
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

            private Builder customerName(String customerName) {
                this.customerName = customerName;
                return this;
            }

            private StaticImmutableItem build() {
                return new StaticImmutableItem(this);
            }
        }
    }
}

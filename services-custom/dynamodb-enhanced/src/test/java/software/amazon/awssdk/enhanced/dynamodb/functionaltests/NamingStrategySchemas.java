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

import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.primaryPartitionKey;
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.primarySortKey;
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.secondaryPartitionKey;

import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.document.DocumentTableSchema;
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument;
import software.amazon.awssdk.enhanced.dynamodb.mapper.AttributeNamingStrategies;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticImmutableTableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;

public final class NamingStrategySchemas {
    private NamingStrategySchemas() {
    }

    public static TableSchema<MutableRecord> mutableSchema() {
        return StaticTableSchema.builder(MutableRecord.class)
            .newItemSupplier(MutableRecord::new)
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .addAttribute(String.class, a -> a.storedName("PK")
                .getter(MutableRecord::getId)
                .setter(MutableRecord::setId)
                .tags(primaryPartitionKey()))
            .addAttribute(String.class, a -> a.name("orderDate")
                .getter(MutableRecord::getOrderDate)
                .setter(MutableRecord::setOrderDate)
                .tags(primarySortKey()))
            .addAttribute(String.class, a -> a.name("customerStatus")
                .getter(MutableRecord::getCustomerStatus)
                .setter(MutableRecord::setCustomerStatus)
                .tags(secondaryPartitionKey("byStatus")))
            .addAttribute(String.class, a -> a.name("customerName")
                .getter(MutableRecord::getCustomerName)
                .setter(MutableRecord::setCustomerName))
            .build();
    }

    public static MutableRecord mutableRecord(String id) {
        MutableRecord record = new MutableRecord();
        record.setId(id);
        record.setOrderDate(AttributeNamingStrategyLocalChecks.ORDER_DATE);
        record.setCustomerStatus(AttributeNamingStrategyLocalChecks.STATUS_VALUE);
        record.setCustomerName(AttributeNamingStrategyLocalChecks.CUSTOMER);
        return record;
    }

    public static TableSchema<ImmutableRecord> immutableSchema() {
        return StaticImmutableTableSchema.builder(ImmutableRecord.class, ImmutableRecord.Builder.class)
            .newItemBuilder(ImmutableRecord::builder, ImmutableRecord.Builder::build)
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .addAttribute(String.class, a -> a.storedName("PK")
                .getter(ImmutableRecord::id)
                .setter(ImmutableRecord.Builder::id)
                .tags(primaryPartitionKey()))
            .addAttribute(String.class, a -> a.name("orderDate")
                .getter(ImmutableRecord::orderDate)
                .setter(ImmutableRecord.Builder::orderDate)
                .tags(primarySortKey()))
            .addAttribute(String.class, a -> a.name("customerStatus")
                .getter(ImmutableRecord::customerStatus)
                .setter(ImmutableRecord.Builder::customerStatus)
                .tags(secondaryPartitionKey("byStatus")))
            .addAttribute(String.class, a -> a.name("customerName")
                .getter(ImmutableRecord::customerName)
                .setter(ImmutableRecord.Builder::customerName))
            .build();
    }

    public static ImmutableRecord immutableRecord(String id) {
        return ImmutableRecord.builder()
            .id(id)
            .orderDate(AttributeNamingStrategyLocalChecks.ORDER_DATE)
            .customerStatus(AttributeNamingStrategyLocalChecks.STATUS_VALUE)
            .customerName(AttributeNamingStrategyLocalChecks.CUSTOMER)
            .build();
    }

    public static DocumentTableSchema documentSchema() {
        return DocumentTableSchema.builder()
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .addStoredName("PK")
            .addIndexPartitionKey(TableMetadata.primaryIndexName(), "PK", AttributeValueType.S)
            .addIndexSortKey(TableMetadata.primaryIndexName(), "orderDate", AttributeValueType.S)
            .addIndexPartitionKey("byStatus", "customerStatus", AttributeValueType.S)
            .build();
    }

    public static EnhancedDocument document(String id) {
        return EnhancedDocument.builder()
            .putString("PK", id)
            .putString("orderDate", AttributeNamingStrategyLocalChecks.ORDER_DATE)
            .putString("customerStatus", AttributeNamingStrategyLocalChecks.STATUS_VALUE)
            .putString("customerName", AttributeNamingStrategyLocalChecks.CUSTOMER)
            .build();
    }

    public static final class MutableRecord {
        private String id;
        private String orderDate;
        private String customerStatus;
        private String customerName;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getOrderDate() {
            return orderDate;
        }

        public void setOrderDate(String orderDate) {
            this.orderDate = orderDate;
        }

        public String getCustomerStatus() {
            return customerStatus;
        }

        public void setCustomerStatus(String customerStatus) {
            this.customerStatus = customerStatus;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }
    }

    public static final class ImmutableRecord {
        private final String id;
        private final String orderDate;
        private final String customerStatus;
        private final String customerName;

        private ImmutableRecord(Builder builder) {
            this.id = builder.id;
            this.orderDate = builder.orderDate;
            this.customerStatus = builder.customerStatus;
            this.customerName = builder.customerName;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String id() {
            return id;
        }

        public String orderDate() {
            return orderDate;
        }

        public String customerStatus() {
            return customerStatus;
        }

        public String customerName() {
            return customerName;
        }

        public static final class Builder {
            private String id;
            private String orderDate;
            private String customerStatus;
            private String customerName;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder orderDate(String orderDate) {
                this.orderDate = orderDate;
                return this;
            }

            public Builder customerStatus(String customerStatus) {
                this.customerStatus = customerStatus;
                return this;
            }

            public Builder customerName(String customerName) {
                this.customerName = customerName;
                return this;
            }

            public ImmutableRecord build() {
                return new ImmutableRecord(this);
            }
        }
    }
}

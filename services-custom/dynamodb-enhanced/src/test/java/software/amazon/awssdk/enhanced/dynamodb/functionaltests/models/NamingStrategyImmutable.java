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

package software.amazon.awssdk.enhanced.dynamodb.functionaltests.models;

import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.AttributeNamingStrategies;
import software.amazon.awssdk.enhanced.dynamodb.mapper.ImmutableTableSchemaParams;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbImmutable(builder = NamingStrategyImmutable.Builder.class)
public class NamingStrategyImmutable {
    private final String id;
    private final String orderDate;
    private final String customerStatus;
    private final String customerName;

    private NamingStrategyImmutable(Builder builder) {
        this.id = builder.id;
        this.orderDate = builder.orderDate;
        this.customerStatus = builder.customerStatus;
        this.customerName = builder.customerName;
    }

    public static TableSchema<NamingStrategyImmutable> schema() {
        return TableSchema.fromImmutableClass(ImmutableTableSchemaParams.builder(NamingStrategyImmutable.class)
            .attributeNamingStrategy(AttributeNamingStrategies.SNAKE_CASE)
            .build());
    }

    public static NamingStrategyImmutable item(String id) {
        return builder().id(id).orderDate("2024-01").customerStatus("ACTIVE").customerName("Ada").build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    public String id() {
        return id;
    }

    @DynamoDbSortKey
    public String orderDate() {
        return orderDate;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "byStatus")
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

        public NamingStrategyImmutable build() {
            return new NamingStrategyImmutable(this);
        }
    }
}

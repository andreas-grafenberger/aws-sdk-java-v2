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

package software.amazon.awssdk.enhanced.dynamodb.mapper.testbeans;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbFlatten;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbImmutable(builder = NamingRegressionImmutable.Builder.class)
public class NamingRegressionImmutable {
    private final String id;
    private final String orderDate;
    private final String customerStatus;
    private final String customerName;
    private final Address address;
    private final Audit audit;

    private NamingRegressionImmutable(Builder builder) {
        this.id = builder.id;
        this.orderDate = builder.orderDate;
        this.customerStatus = builder.customerStatus;
        this.customerName = builder.customerName;
        this.address = builder.address;
        this.audit = builder.audit;
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

    public Address address() {
        return address;
    }

    @DynamoDbFlatten
    public Audit audit() {
        return audit;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String orderDate;
        private String customerStatus;
        private String customerName;
        private Address address;
        private Audit audit;

        private Builder() {
        }

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

        public Builder address(Address address) {
            this.address = address;
            return this;
        }

        public Builder audit(Audit audit) {
            this.audit = audit;
            return this;
        }

        public NamingRegressionImmutable build() {
            return new NamingRegressionImmutable(this);
        }
    }

    @DynamoDbImmutable(builder = NamingRegressionImmutable.Address.Builder.class)
    public static final class Address {
        private final String postalCode;

        private Address(Builder builder) {
            this.postalCode = builder.postalCode;
        }

        public String postalCode() {
            return postalCode;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String postalCode;

            private Builder() {
            }

            public Builder postalCode(String postalCode) {
                this.postalCode = postalCode;
                return this;
            }

            public Address build() {
                return new Address(this);
            }
        }
    }

    @DynamoDbImmutable(builder = NamingRegressionImmutable.Audit.Builder.class)
    public static final class Audit {
        private final String createdAt;

        private Audit(Builder builder) {
            this.createdAt = builder.createdAt;
        }

        public String createdAt() {
            return createdAt;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String createdAt;

            private Builder() {
            }

            public Builder createdAt(String createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public Audit build() {
                return new Audit(this);
            }
        }
    }
}

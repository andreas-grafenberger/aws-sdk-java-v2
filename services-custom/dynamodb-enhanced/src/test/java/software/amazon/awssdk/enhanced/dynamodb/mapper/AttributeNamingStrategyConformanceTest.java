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
import static org.assertj.core.api.Assertions.catchThrowable;
import static software.amazon.awssdk.enhanced.dynamodb.internal.AttributeValues.stringValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbAtomicCounter;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbFlatten;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Checks how bean, immutable, and static schemas store attribute names.
 */
class AttributeNamingStrategyConformanceTest {
    private static final AttributeNamingStrategy LOWER_SNAKE_CASE = BuiltInAttributeNamingStrategy.LOWER_SNAKE_CASE;
    private static final AttributeNamingStrategy PREFIXED = attributeName -> "app_" + attributeName;

    @Test
    void attributeNames_whenBeanUsesStrategy_storesTransformedName() {
        TableSchema<Plain> schema = bean(Plain.class, PREFIXED);

        assertThat(schema.attributeNames()).containsExactly("app_customerId");
    }

    @Test
    void attributeNames_whenStaticSchemaUsesStrategy_storesTransformedName() {
        TableSchema<Plain> schema = StaticTableSchema.builder(Plain.class)
                                                     .newItemSupplier(Plain::new)
                                                     .attributeNamingStrategy(PREFIXED)
                                                     .addAttribute(String.class, a -> a.name("customerId")
                                                                                       .getter(Plain::getCustomerId)
                                                                                       .setter(Plain::setCustomerId))
                                                     .build();

        assertThat(schema.attributeNames()).containsExactly("app_customerId");
    }

    @Test
    void attributeNames_whenStrategyIsSetAfterAttributes_storesTransformedName() {
        TableSchema<Plain> schema = StaticTableSchema.builder(Plain.class)
                                                     .newItemSupplier(Plain::new)
                                                     .addAttribute(String.class, a -> a.name("customerId")
                                                                                       .getter(Plain::getCustomerId)
                                                                                       .setter(Plain::setCustomerId))
                                                     .attributeNamingStrategy(LOWER_SNAKE_CASE)
                                                     .build();

        assertThat(schema.attributeNames()).containsExactly("customer_id");
    }

    @Test
    void itemToMap_whenDiscoveredChildrenInheritStrategy_storesResolvedNames() {
        TableSchema<Shipment> schema = bean(Shipment.class, LOWER_SNAKE_CASE);

        GeoPoint geoPoint = new GeoPoint();
        geoPoint.setLatitudeValue("52.1");
        Address address = new Address();
        address.setGeoPoint(geoPoint);
        LineItem lineItem = new LineItem();
        lineItem.setProductCode("p-1");
        ImmutableTag tag = ImmutableTag.builder().tagValue("fragile").build();

        Shipment shipment = new Shipment();
        shipment.setShippingAddress(address);
        shipment.setLineItems(Collections.singletonList(lineItem));
        shipment.setItemsByCode(Collections.singletonMap("p-1", lineItem));
        shipment.setHandlingTag(tag);

        Map<String, AttributeValue> mapped = schema.itemToMap(shipment, true);

        assertThat(mapped).containsOnlyKeys("shipping_address", "line_items", "items_by_code", "handling_tag");
        assertThat(mapped.get("shipping_address").m()).containsOnlyKeys("geo_point");
        assertThat(mapped.get("shipping_address").m().get("geo_point").m())
            .containsOnlyKeys("latitude_value")
            .containsEntry("latitude_value", stringValue("52.1"));
        assertThat(mapped.get("line_items").l().get(0).m())
            .containsOnlyKeys("product_code")
            .containsEntry("product_code", stringValue("p-1"));
        assertThat(mapped.get("items_by_code").m()).containsOnlyKeys("p-1");
        assertThat(mapped.get("items_by_code").m().get("p-1").m())
            .containsOnlyKeys("product_code")
            .containsEntry("product_code", stringValue("p-1"));
        assertThat(mapped.get("handling_tag").m())
            .containsOnlyKeys("tag_value")
            .containsEntry("tag_value", stringValue("fragile"));
    }

    @Test
    void create_whenModelIsInsideSet_throwsIllegalStateException() {
        assertThatThrownBy(() -> bean(SetHolder.class, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Converter not found");
        assertThatThrownBy(() -> bean(SetHolder.class, LOWER_SNAKE_CASE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Converter not found");
    }

    @Test
    void attributeNames_whenFlattenedImmutableChildInheritsStrategy_storesResolvedNames() {
        TableSchema<ImmutableParent> schema = TableSchema.fromImmutableClass(
            ImmutableTableSchemaParams.builder(ImmutableParent.class)
                                      .attributeNamingStrategy(LOWER_SNAKE_CASE)
                                      .build());

        assertThat(schema.attributeNames()).containsExactlyInAnyOrder("parent_id", "phone_number");
    }

    @Test
    void attributeNames_whenSchemaExtendsAnother_keepsSuperSchemaNames() {
        StaticTableSchema<BaseRecord> superSchema =
            StaticTableSchema.builder(BaseRecord.class)
                             .addAttribute(String.class, a -> a.name("createdBy")
                                                               .getter(BaseRecord::getCreatedBy)
                                                               .setter(BaseRecord::setCreatedBy))
                             .build();

        TableSchema<SubRecord> schema =
            StaticTableSchema.builder(SubRecord.class)
                             .newItemSupplier(SubRecord::new)
                             .attributeNamingStrategy(LOWER_SNAKE_CASE)
                             .extend(superSchema)
                             .addAttribute(String.class, a -> a.name("customerId")
                                                               .getter(SubRecord::getCustomerId)
                                                               .setter(SubRecord::setCustomerId))
                             .build();

        assertThat(schema.attributeNames()).containsExactlyInAnyOrder("createdBy", "customer_id");
    }

    @Test
    void itemToMap_whenChildSchemaIsSupplied_keepsThatSchemasNames() {
        TableSchema<Plain> unconfiguredChild = plainStaticSchema(null);
        TableSchema<Plain> upperSnakeChild = plainStaticSchema(BuiltInAttributeNamingStrategy.UPPER_SNAKE_CASE);

        TableSchema<Holder> schema =
            StaticTableSchema.builder(Holder.class)
                             .newItemSupplier(Holder::new)
                             .attributeNamingStrategy(LOWER_SNAKE_CASE)
                             .addAttribute(EnhancedType.documentOf(Plain.class, unconfiguredChild),
                                           a -> a.name("firstChild").getter(Holder::getFirst).setter(Holder::setFirst))
                             .addAttribute(EnhancedType.documentOf(Plain.class, upperSnakeChild),
                                           a -> a.name("secondChild").getter(Holder::getSecond).setter(Holder::setSecond))
                             .build();

        Plain child = new Plain();
        child.setCustomerId("c-1");
        Holder holder = new Holder();
        holder.setFirst(child);
        holder.setSecond(child);

        Map<String, AttributeValue> mapped = schema.itemToMap(holder, true);
        assertThat(mapped.get("first_child").m()).containsOnlyKeys("customerId");
        assertThat(mapped.get("second_child").m()).containsOnlyKeys("CUSTOMER_ID");
    }

    @Test
    void itemToMap_whenMapIsFlattened_keepsMapKeys() {
        TableSchema<WithExtraFields> schema = bean(WithExtraFields.class, LOWER_SNAKE_CASE);

        WithExtraFields item = new WithExtraFields();
        item.setRecordId("r-1");
        item.setExtraFields(Collections.singletonMap("favoriteColor", "blue"));

        assertThat(schema.itemToMap(item, true)).containsOnlyKeys("record_id", "favoriteColor")
                                                .containsEntry("record_id", stringValue("r-1"))
                                                .containsEntry("favoriteColor", stringValue("blue"));
    }

    @Test
    void itemToMap_whenConverterOwnsTheValue_keepsConverterNames() {
        TableSchema<Invoice> schema = bean(Invoice.class, LOWER_SNAKE_CASE);

        Invoice invoice = new Invoice();
        invoice.setTotalPrice(new Money(1250));

        Map<String, AttributeValue> mapped = schema.itemToMap(invoice, true);
        assertThat(mapped.get("total_price").m()).containsOnlyKeys("amountInCents");
    }

    @Test
    void tableMetadata_whenStrategyIsApplied_usesResolvedNames() {
        TableMetadata metadata = bean(MetadataRecord.class, LOWER_SNAKE_CASE).tableMetadata();

        assertThat(metadata.primaryPartitionKey()).isEqualTo("record_id");
        assertThat(metadata.primarySortKey()).contains("sort_value");
        assertThat(metadata.indexPartitionKey("byStatus")).isEqualTo("order_status");
        assertThat(metadata.indexSortKey("byStatus")).contains("created_date");
        assertThat(metadata.customMetadataObject("VersionedRecordExtension:VersionAttribute", String.class))
            .contains("record_version");
        assertThat(metadata.customMetadataObject("AtomicCounter:Counters", Map.class))
            .hasValueSatisfying(counters -> assertThat(counters).containsOnlyKeys("view_count"));
    }

    @Test
    void create_whenNamesResolveToTheSameValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> bean(Colliding.class, LOWER_SNAKE_CASE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("customer_id");
    }

    @Test
    void create_whenFlattenedChildCollides_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> bean(CollidingWithFlattened.class, LOWER_SNAKE_CASE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("customer_id");
    }

    @Test
    void create_whenStrategyThrows_propagatesThatException() {
        IllegalStateException failure = new IllegalStateException("naming failed");

        assertThatThrownBy(() -> bean(Plain.class, attributeName -> {
            throw failure;
        })).isSameAs(failure);
    }

    @Test
    void itemToMap_whenBeanReferencesItself_storesResolvedNames() {
        TableSchema<TreeNode> schema = bean(TreeNode.class, LOWER_SNAKE_CASE);

        TreeNode child = new TreeNode();
        child.setNodeName("leaf");
        TreeNode root = new TreeNode();
        root.setNodeName("root");
        root.setChildNode(child);

        Map<String, AttributeValue> mapped = schema.itemToMap(root, true);
        assertThat(mapped).containsOnlyKeys("node_name", "child_node")
                          .containsEntry("node_name", stringValue("root"));
        assertThat(mapped.get("child_node").m()).containsOnlyKeys("node_name", "child_node")
                                                 .containsEntry("node_name", stringValue("leaf"))
                                                 .containsEntry("child_node", AttributeValue.fromNul(true));
    }

    // BeanTableSchema.recursiveCreate starts a new MetaTableSchemaCache for each discovered class, so two beans that
    // reference each other recurse without end. This predates the strategy, which neither fixes nor worsens it.
    @Test
    void create_whenBeansReferenceEachOther_failsWithStackOverflow() {
        BeanTableSchema.clearSchemaCache();
        assertThat(causeChain(catchThrowable(() -> bean(Author.class, null))))
            .hasAtLeastOneElementOfType(StackOverflowError.class);
        assertThat(causeChain(catchThrowable(() -> bean(Author.class, LOWER_SNAKE_CASE))))
            .hasAtLeastOneElementOfType(StackOverflowError.class);
    }

    private static List<Throwable> causeChain(Throwable throwable) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            chain.add(current);
        }
        return chain;
    }

    @Test
    void schema_whenBeanStrategyIsOmitted_reusesTheClassCache() {
        assertThat(bean(Plain.class, null)).isSameAs(TableSchema.fromBean(Plain.class));
    }

    @Test
    void schema_whenBeanUsesStrategy_buildsANewSchema() {
        AttributeNamingStrategy strategy = BuiltInAttributeNamingStrategy.LOWER_SNAKE_CASE;

        assertThat(bean(Plain.class, strategy)).isNotSameAs(bean(Plain.class, strategy));
    }

    @Test
    void attributeNames_whenBeanUsesUpperSnakeCase_storesUpperSnakeCase() {
        assertThat(bean(Plain.class, BuiltInAttributeNamingStrategy.UPPER_SNAKE_CASE).attributeNames())
            .containsExactly("CUSTOMER_ID");
    }

    @Test
    void schema_whenImmutableStrategyIsOmitted_reusesTheClassCache() {
        assertThat(immutable(ImmutableTag.class, null)).isSameAs(TableSchema.fromImmutableClass(ImmutableTag.class));
    }

    @Test
    void schema_whenImmutableUsesStrategy_buildsANewSchema() {
        AttributeNamingStrategy strategy = BuiltInAttributeNamingStrategy.LOWER_SNAKE_CASE;

        assertThat(immutable(ImmutableTag.class, strategy)).isNotSameAs(immutable(ImmutableTag.class, strategy));
    }

    private static <T> TableSchema<T> immutable(Class<T> immutableClass, AttributeNamingStrategy strategy) {
        return TableSchema.fromImmutableClass(
            ImmutableTableSchemaParams.builder(immutableClass).attributeNamingStrategy(strategy).build());
    }

    private static <T> TableSchema<T> bean(Class<T> beanClass, AttributeNamingStrategy strategy) {
        return TableSchema.fromBean(BeanTableSchemaParams.builder(beanClass).attributeNamingStrategy(strategy).build());
    }

    private static TableSchema<Plain> plainStaticSchema(AttributeNamingStrategy strategy) {
        return StaticTableSchema.builder(Plain.class)
                                .newItemSupplier(Plain::new)
                                .attributeNamingStrategy(strategy)
                                .addAttribute(String.class, a -> a.name("customerId")
                                                                  .getter(Plain::getCustomerId)
                                                                  .setter(Plain::setCustomerId))
                                .build();
    }

    @DynamoDbBean
    public static class Plain {
        private String customerId;

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }
    }

    public static class Holder {
        private Plain first;
        private Plain second;

        public Plain getFirst() {
            return first;
        }

        public void setFirst(Plain first) {
            this.first = first;
        }

        public Plain getSecond() {
            return second;
        }

        public void setSecond(Plain second) {
            this.second = second;
        }
    }

    @DynamoDbBean
    public static class Shipment {
        private Address shippingAddress;
        private List<LineItem> lineItems;
        private Map<String, LineItem> itemsByCode;
        private ImmutableTag handlingTag;

        public Address getShippingAddress() {
            return shippingAddress;
        }

        public void setShippingAddress(Address shippingAddress) {
            this.shippingAddress = shippingAddress;
        }

        public List<LineItem> getLineItems() {
            return lineItems;
        }

        public void setLineItems(List<LineItem> lineItems) {
            this.lineItems = lineItems;
        }

        public Map<String, LineItem> getItemsByCode() {
            return itemsByCode;
        }

        public void setItemsByCode(Map<String, LineItem> itemsByCode) {
            this.itemsByCode = itemsByCode;
        }

        public ImmutableTag getHandlingTag() {
            return handlingTag;
        }

        public void setHandlingTag(ImmutableTag handlingTag) {
            this.handlingTag = handlingTag;
        }
    }

    @DynamoDbBean
    public static class Address {
        private GeoPoint geoPoint;

        public GeoPoint getGeoPoint() {
            return geoPoint;
        }

        public void setGeoPoint(GeoPoint geoPoint) {
            this.geoPoint = geoPoint;
        }
    }

    @DynamoDbBean
    public static class GeoPoint {
        private String latitudeValue;

        public String getLatitudeValue() {
            return latitudeValue;
        }

        public void setLatitudeValue(String latitudeValue) {
            this.latitudeValue = latitudeValue;
        }
    }

    @DynamoDbBean
    public static class LineItem {
        private String productCode;

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }
    }

    @DynamoDbImmutable(builder = ImmutableTag.Builder.class)
    public static final class ImmutableTag {
        private final String tagValue;

        private ImmutableTag(Builder builder) {
            this.tagValue = builder.tagValue;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String tagValue() {
            return tagValue;
        }

        public static final class Builder {
            private String tagValue;

            public Builder tagValue(String tagValue) {
                this.tagValue = tagValue;
                return this;
            }

            public ImmutableTag build() {
                return new ImmutableTag(this);
            }
        }
    }

    @DynamoDbBean
    public static class SetHolder {
        private Set<LineItem> lineItemSet;

        public Set<LineItem> getLineItemSet() {
            return lineItemSet;
        }

        public void setLineItemSet(Set<LineItem> lineItemSet) {
            this.lineItemSet = lineItemSet;
        }
    }

    @DynamoDbImmutable(builder = ImmutableParent.Builder.class)
    public static final class ImmutableParent {
        private final String parentId;
        private final ImmutableContact contact;

        private ImmutableParent(Builder builder) {
            this.parentId = builder.parentId;
            this.contact = builder.contact;
        }

        public static Builder builder() {
            return new Builder();
        }

        @DynamoDbPartitionKey
        public String parentId() {
            return parentId;
        }

        @DynamoDbFlatten
        public ImmutableContact contact() {
            return contact;
        }

        public static final class Builder {
            private String parentId;
            private ImmutableContact contact;

            public Builder parentId(String parentId) {
                this.parentId = parentId;
                return this;
            }

            public Builder contact(ImmutableContact contact) {
                this.contact = contact;
                return this;
            }

            public ImmutableParent build() {
                return new ImmutableParent(this);
            }
        }
    }

    @DynamoDbImmutable(builder = ImmutableContact.Builder.class)
    public static final class ImmutableContact {
        private final String phoneNumber;

        private ImmutableContact(Builder builder) {
            this.phoneNumber = builder.phoneNumber;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String phoneNumber() {
            return phoneNumber;
        }

        public static final class Builder {
            private String phoneNumber;

            public Builder phoneNumber(String phoneNumber) {
                this.phoneNumber = phoneNumber;
                return this;
            }

            public ImmutableContact build() {
                return new ImmutableContact(this);
            }
        }
    }

    public static class BaseRecord {
        private String createdBy;

        public String getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
        }
    }

    public static class SubRecord extends BaseRecord {
        private String customerId;

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }
    }

    @DynamoDbBean
    public static class WithExtraFields {
        private String recordId;
        private Map<String, String> extraFields;

        public String getRecordId() {
            return recordId;
        }

        public void setRecordId(String recordId) {
            this.recordId = recordId;
        }

        @DynamoDbFlatten
        public Map<String, String> getExtraFields() {
            return extraFields;
        }

        public void setExtraFields(Map<String, String> extraFields) {
            this.extraFields = extraFields;
        }
    }

    public static final class Money {
        private final long amountInCents;

        public Money(long amountInCents) {
            this.amountInCents = amountInCents;
        }

        public long amountInCents() {
            return amountInCents;
        }
    }

    public static class MoneyConverter implements AttributeConverter<Money> {
        @Override
        public AttributeValue transformFrom(Money input) {
            return AttributeValue.fromM(Collections.singletonMap(
                "amountInCents", AttributeValue.fromN(Long.toString(input.amountInCents()))));
        }

        @Override
        public Money transformTo(AttributeValue input) {
            return new Money(Long.parseLong(input.m().get("amountInCents").n()));
        }

        @Override
        public EnhancedType<Money> type() {
            return EnhancedType.of(Money.class);
        }

        @Override
        public AttributeValueType attributeValueType() {
            return AttributeValueType.M;
        }
    }

    @DynamoDbBean
    public static class Invoice {
        private Money totalPrice;

        @DynamoDbConvertedBy(MoneyConverter.class)
        public Money getTotalPrice() {
            return totalPrice;
        }

        public void setTotalPrice(Money totalPrice) {
            this.totalPrice = totalPrice;
        }
    }

    @DynamoDbBean
    public static class MetadataRecord {
        private String recordId;
        private String sortValue;
        private String orderStatus;
        private String createdDate;
        private Long recordVersion;
        private Long viewCount;

        @DynamoDbPartitionKey
        public String getRecordId() {
            return recordId;
        }

        public void setRecordId(String recordId) {
            this.recordId = recordId;
        }

        @DynamoDbSortKey
        public String getSortValue() {
            return sortValue;
        }

        public void setSortValue(String sortValue) {
            this.sortValue = sortValue;
        }

        @DynamoDbSecondaryPartitionKey(indexNames = "byStatus")
        public String getOrderStatus() {
            return orderStatus;
        }

        public void setOrderStatus(String orderStatus) {
            this.orderStatus = orderStatus;
        }

        @DynamoDbSecondarySortKey(indexNames = "byStatus")
        public String getCreatedDate() {
            return createdDate;
        }

        public void setCreatedDate(String createdDate) {
            this.createdDate = createdDate;
        }

        @DynamoDbVersionAttribute
        public Long getRecordVersion() {
            return recordVersion;
        }

        public void setRecordVersion(Long recordVersion) {
            this.recordVersion = recordVersion;
        }

        @DynamoDbAtomicCounter
        public Long getViewCount() {
            return viewCount;
        }

        public void setViewCount(Long viewCount) {
            this.viewCount = viewCount;
        }
    }

    @DynamoDbBean
    public static class Colliding {
        private String customerId;
        private String customerIdLegacy;

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public String getCustomer_id() {
            return customerIdLegacy;
        }

        public void setCustomer_id(String customerIdLegacy) {
            this.customerIdLegacy = customerIdLegacy;
        }
    }

    @DynamoDbBean
    public static class CollidingWithFlattened {
        private String customerId;
        private CustomerReference reference;

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        @DynamoDbFlatten
        public CustomerReference getReference() {
            return reference;
        }

        public void setReference(CustomerReference reference) {
            this.reference = reference;
        }
    }

    @DynamoDbBean
    public static class CustomerReference {
        private String customerID;

        public String getCustomerID() {
            return customerID;
        }

        public void setCustomerID(String customerID) {
            this.customerID = customerID;
        }
    }

    @DynamoDbBean
    public static class TreeNode {
        private String nodeName;
        private TreeNode childNode;

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public TreeNode getChildNode() {
            return childNode;
        }

        public void setChildNode(TreeNode childNode) {
            this.childNode = childNode;
        }
    }

    @DynamoDbBean
    public static class Author {
        private String authorName;
        private Book latestBook;

        public String getAuthorName() {
            return authorName;
        }

        public void setAuthorName(String authorName) {
            this.authorName = authorName;
        }

        public Book getLatestBook() {
            return latestBook;
        }

        public void setLatestBook(Book latestBook) {
            this.latestBook = latestBook;
        }
    }

    @DynamoDbBean
    public static class Book {
        private String bookTitle;
        private Author bookAuthor;

        public String getBookTitle() {
            return bookTitle;
        }

        public void setBookTitle(String bookTitle) {
            this.bookTitle = bookTitle;
        }

        public Author getBookAuthor() {
            return bookAuthor;
        }

        public void setBookAuthor(Author bookAuthor) {
            this.bookAuthor = bookAuthor;
        }
    }
}

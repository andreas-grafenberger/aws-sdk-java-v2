package software.amazon.awssdk.enhanced.dynamodb.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.awssdk.enhanced.dynamodb.internal.AttributeValues.numberValue;
import static software.amazon.awssdk.enhanced.dynamodb.internal.AttributeValues.stringValue;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.utils.ImmutableMap;

/**
 * The issue occurs only with {@link BeanTableSchema}, which discovers properties with
 * {@link Introspector#getBeanInfo(Class)}.
 *
 * <p>{@code Introspector} treats {@code getSomeInt()} and a primitive-boolean {@code isSomeInt()} as accessors for
 * the same {@code someInt} property. JavaBeans permits {@code isX()} as the getter form for a primitive-boolean
 * property, and the JDK {@code Introspector} gives that form priority when both {@code getX()} and {@code isX()}
 * are present. The resulting {@code someInt} {@code PropertyDescriptor} uses {@code isSomeInt()} as its read method
 * and has type {@code boolean}. {@code setSomeInt(int)} cannot be its write method because the types differ.
 *
 * <p>When {@code isSomeInt()} is annotated with {@link DynamoDbIgnore}, {@code BeanTableSchema} considers the
 * single {@code someInt} property ignored. It receives the already-resolved {@code PropertyDescriptor}, so it
 * cannot discover or fall back to {@code getSomeInt()}.
 *
 * <p>{@code @DynamoDbImmutable} creates an immutable schema that uses {@code ImmutableIntrospector}, not JavaBeans
 * introspection. It filters ignored getters, then processes every remaining getter independently. It normalizes
 * {@code isSomeInt()} to {@code someInt} and requires a builder method with that name and a matching parameter type.
 * It does not choose {@code isSomeInt()} over {@code someInt()} as {@code Introspector} does.
 *
 * <p>When {@code isSomeInt()} is ignored, it is removed before getter processing and {@code someInt()} can match
 * {@code Builder.someInt(int)}. Without {@link DynamoDbIgnore}, both getters normalize to {@code someInt}. One
 * getter consumes {@code Builder.someInt(int)}, and the other fails because no matching builder method remains or
 * because its parameter type does not match. Adding {@code Builder.someInt(boolean)} does not make the immutable
 * schema equivalent to the bean case. The immutable introspector indexes builder methods by normalized name first,
 * so the two builder overloads cause a duplicate {@code someInt} key error before property descriptors are created.
 *
 * <p>Explicit schema construction, including {@link TableSchema#builder(Class)} and {@code StaticTableSchema},
 * does not infer properties from accessor methods. The caller supplies each attribute's getter and setter, so
 * JavaBeans {@code isX()} precedence does not apply.
 *
 * @see <a href="https://github.com/aws/aws-sdk-java-v2/issues/7328">Issue #7328</a>
 */
class ReproduceIssue7328Test {

    @Test
    void beanSchema_whenIgnoredIsGetterDiffersFromGetGetter_thenMapsAttribute() {
        RecordWithDifferentIsAndGetGetters record = new RecordWithDifferentIsAndGetGetters();
        record.setPk("1");
        record.setSomeInt(123);

        verifyBeanSchemaMapping(RecordWithDifferentIsAndGetGetters.class, record);
    }

    @Test
    void beanSchema_whenIgnoredIsGetterMatchesGetGetter_thenDoesNotMapAttribute() {
        RecordWithMatchingIsAndGetGetters record = new RecordWithMatchingIsAndGetGetters();
        record.setPk("1");
        record.setSomeInt(123);

        verifyBeanSchemaMapping(RecordWithMatchingIsAndGetGetters.class, record);
    }

    private <T> void verifyBeanSchemaMapping(Class<T> clazz, T record) {
        BeanTableSchema<T> tableSchema = BeanTableSchema.create(clazz);

        System.out.println("--------------------");
        printClassPropertyDescriptorsUsingIntrospector(clazz);
        System.out.println("--------------------");
        printClassDeclaredMethods(clazz);
        System.out.println("--------------------");

        Map<String, AttributeValue> item = tableSchema.itemToMap(record, true);
        System.out.println("Table schema attribute names: " + tableSchema.attributeNames());
        System.out.println("Mapped item: " + item);

        assertThat(tableSchema.attributeNames()).containsExactlyInAnyOrder("pk", "SomeInt");
        assertThat(item).containsExactlyInAnyOrderEntriesOf(ImmutableMap.of(
            "pk", stringValue("1"),
            "SomeInt", numberValue(123)
        ));
    }

    private void printClassPropertyDescriptorsUsingIntrospector(Class<?> clazz) {
        BeanInfo beanInfo = null;
        try {
            beanInfo = Introspector.getBeanInfo(clazz);
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Property descriptors for class (uses Introspector.getBeanInfo): " + clazz.getName());
        Arrays.stream(beanInfo.getPropertyDescriptors())
              .forEach(System.out::println);
    }

    private void printClassDeclaredMethods(Class<?> clazz) {
        System.out.println("All declared methods for class (uses clazz.getDeclaredMethods): " + clazz.getName());
        Arrays.stream(clazz.getDeclaredMethods())
              .forEach(System.out::println);
    }

    /**
     * Control model for the expected mapping behavior. {@link #getSomeInt()} and {@link #setSomeInt(int)} define the
     * mapped {@code someInt} property. {@link #isSomeInt1()} defines a separate boolean {@code someInt1} property.
     *
     * <p>{@link DynamoDbIgnore} excludes {@code someInt1} only, so the {@code SomeInt} DynamoDB attribute remains
     * mapped.
     */
    @DynamoDbBean
    public static class RecordWithDifferentIsAndGetGetters {
        private String pk;
        private int someInt;

        @DynamoDbPartitionKey
        public String getPk() {
            return pk;
        }

        public void setPk(String pk) {
            this.pk = pk;
        }

        /**
         * JavaBeans recognizes this as the {@code someInt} property.
         * {@link #isSomeInt1()} is a separate {@code someInt1} property, so this getter remains mapped.
         */
        @DynamoDbAttribute("SomeInt")
        public int getSomeInt() {
            return someInt;
        }

        public void setSomeInt(int someInt) {
            this.someInt = someInt;
        }

        /**
         * JavaBeans recognizes this as the distinct {@code someInt1} property.
         * {@link DynamoDbIgnore} excludes only that property and does not affect {@code getSomeInt()}.
         */
        @DynamoDbIgnore
        public boolean isSomeInt1() {
            return false;
        }
    }

    /**
     * Reproduction model for the unexpected mapping behavior. {@link #getSomeInt()} and {@link #isSomeInt()} both
     * resolve to the {@code someInt} JavaBeans property. {@code Introspector} selects the primitive-boolean
     * {@code isSomeInt()} getter over {@code getSomeInt()}.
     *
     * <p>{@link DynamoDbIgnore} on {@code isSomeInt()} therefore excludes the shared property, including the
     * {@code SomeInt} DynamoDB attribute configured on {@code getSomeInt()}.
     */
    @DynamoDbBean
    public static class RecordWithMatchingIsAndGetGetters {
        private String pk;
        private int someInt;

        @DynamoDbPartitionKey
        public String getPk() {
            return pk;
        }

        public void setPk(String pk) {
            this.pk = pk;
        }

        /**
         * JavaBeans groups this getter with {@link #isSomeInt()} as the {@code someInt} property.
         * {@code Introspector} does not select this getter when the primitive-boolean {@code isSomeInt()} exists.
         */
        @DynamoDbAttribute("SomeInt")
        public int getSomeInt() {
            return someInt;
        }

        public void setSomeInt(int someInt) {
            this.someInt = someInt;
        }

        /**
         * JavaBeans treats this as the {@code someInt} getter and gives it priority over {@link #getSomeInt()}.
         * {@link DynamoDbIgnore} then causes {@code BeanTableSchema} to omit the shared property.
         */
        @DynamoDbIgnore
        public boolean isSomeInt() {
            return false;
        }
    }
}

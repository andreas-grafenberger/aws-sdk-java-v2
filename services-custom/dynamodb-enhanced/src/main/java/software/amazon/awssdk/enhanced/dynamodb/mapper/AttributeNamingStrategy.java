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

import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.annotations.ThreadSafe;

/**
 * Transforms one attribute name into the name DynamoDB stores.
 *
 * <p>
 * The function must return the same non-empty name for the same input on every call and every JVM. It must not depend
 * on the default locale. Use {@code Locale.ROOT} for any case change.
 *
 * <p>
 * A schema builder applies the strategy while the schema is built. An omitted strategy keeps the name that was
 * supplied. {@link AttributeNamingStrategies} provides the built-in strategies. A lambda is a custom strategy:
 *
 * {@snippet :
 * StaticTableSchema.builder(Customer.class)
 *                   .attributeNamingStrategy(name -> name.toLowerCase(Locale.ROOT))
 *                   .build();
 * }
 */
@SdkPublicApi
@FunctionalInterface
@ThreadSafe
public interface AttributeNamingStrategy {
    /**
     * Returns the name to store for the supplied attribute name.
     *
     * @param attributeName the property name, {@code name(...)} value, document key, or JSON key
     * @return the stored name, which must be non-empty and stable for this input
     */
    String transform(String attributeName);
}

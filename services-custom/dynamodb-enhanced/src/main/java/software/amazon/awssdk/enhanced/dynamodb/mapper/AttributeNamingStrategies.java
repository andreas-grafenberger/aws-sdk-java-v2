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
import software.amazon.awssdk.enhanced.dynamodb.internal.mapper.BuiltInAttributeNamingStrategy;

/**
 * Built-in {@link AttributeNamingStrategy} implementations.
 *
 * <p>
 * {@link #SNAKE_CASE} stores {@code httpRequest} as {@code http_request}, {@code HTTPResponse} as
 * {@code http_response}, and {@code URLs} as {@code ur_ls}. {@link #LOWER_CAMEL_CASE} changes only the first
 * character to lower case, so {@code HTTPResponse} is stored as {@code hTTPResponse}. {@link #UPPER_CAMEL_CASE}
 * changes only the first character to upper case. {@link #IDENTITY} stores the supplied name unchanged.
 * The same input produces the same stored name under every JVM default locale.
 */
// CHECKSTYLE:OFF - The published name is plural because it is the set of strategies, not one strategy.
@SdkPublicApi
public final class AttributeNamingStrategies {
    public static final AttributeNamingStrategy SNAKE_CASE = BuiltInAttributeNamingStrategy.SNAKE_CASE;
    public static final AttributeNamingStrategy LOWER_CAMEL_CASE = BuiltInAttributeNamingStrategy.LOWER_CAMEL_CASE;
    public static final AttributeNamingStrategy UPPER_CAMEL_CASE = BuiltInAttributeNamingStrategy.UPPER_CAMEL_CASE;
    public static final AttributeNamingStrategy IDENTITY = BuiltInAttributeNamingStrategy.IDENTITY;

    private AttributeNamingStrategies() {
    }
    // CHECKSTYLE:ON
}

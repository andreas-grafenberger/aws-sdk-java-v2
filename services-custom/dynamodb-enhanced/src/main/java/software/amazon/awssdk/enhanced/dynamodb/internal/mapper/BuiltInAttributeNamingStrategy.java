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

package software.amazon.awssdk.enhanced.dynamodb.internal.mapper;

import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.enhanced.dynamodb.mapper.AttributeNamingStrategy;

/**
 * The built-in naming strategies. {@link #toString()} is the constant name used in error messages.
 * Only these instances are cached with a bean or immutable schema.
 */
@SdkInternalApi
public enum BuiltInAttributeNamingStrategy implements AttributeNamingStrategy {
    SNAKE_CASE {
        @Override
        public String transform(String attributeName) {
            return toSnakeCase(attributeName);
        }
    },
    LOWER_CAMEL_CASE {
        @Override
        public String transform(String attributeName) {
            return changeFirstCharacter(attributeName, true);
        }
    },
    UPPER_CAMEL_CASE {
        @Override
        public String transform(String attributeName) {
            return changeFirstCharacter(attributeName, false);
        }
    },
    IDENTITY {
        @Override
        public String transform(String attributeName) {
            return attributeName;
        }
    };

    /**
     * Inserts '_' before an uppercase letter that follows a lowercase letter or a digit, and before the last uppercase
     * letter of a run when a lowercase letter follows. Every other character stays in place. Each character is then
     * lowercased with {@link Character#toLowerCase(char)}.
     */
    private static String toSnakeCase(String attributeName) {
        StringBuilder storedName = new StringBuilder(attributeName.length() + 8);
        for (int index = 0; index < attributeName.length(); index++) {
            char current = attributeName.charAt(index);
            if (insertUnderscore(attributeName, index)) {
                storedName.append('_');
            }
            storedName.append(Character.toLowerCase(current));
        }
        return storedName.toString();
    }

    private static boolean insertUnderscore(String attributeName, int index) {
        if (index == 0) {
            return false;
        }
        char current = attributeName.charAt(index);
        if (!Character.isUpperCase(current)) {
            return false;
        }
        char previous = attributeName.charAt(index - 1);
        if (Character.isLowerCase(previous) || Character.isDigit(previous)) {
            return true;
        }
        return Character.isUpperCase(previous)
               && index + 1 < attributeName.length()
               && Character.isLowerCase(attributeName.charAt(index + 1));
    }

    private static String changeFirstCharacter(String attributeName, boolean lower) {
        if (attributeName.isEmpty()) {
            return attributeName;
        }
        char first = attributeName.charAt(0);
        char changed = lower ? Character.toLowerCase(first) : Character.toUpperCase(first);
        if (changed == first) {
            return attributeName;
        }
        return changed + attributeName.substring(1);
    }
}

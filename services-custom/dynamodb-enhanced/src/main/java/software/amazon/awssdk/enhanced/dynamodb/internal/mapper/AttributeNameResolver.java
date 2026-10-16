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

import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.enhanced.dynamodb.internal.EnhancedClientUtils;
import software.amazon.awssdk.enhanced.dynamodb.mapper.AttributeNamingStrategies;
import software.amazon.awssdk.enhanced.dynamodb.mapper.AttributeNamingStrategy;

/**
 * Applies one naming strategy and rejects a null result, an empty result, a duplicate stored name, and two stored
 * names that share one expression placeholder. A null strategy returns each input unchanged and skips those checks.
 */
@SdkInternalApi
public final class AttributeNameResolver {
    private final AttributeNamingStrategy strategy;
    private final String schemaName;
    private final Map<String, NameOrigin> storedNames = new LinkedHashMap<>();
    private final Map<String, String> placeholderToStoredName = new LinkedHashMap<>();

    public AttributeNameResolver(AttributeNamingStrategy strategy, String schemaName) {
        this.strategy = strategy;
        this.schemaName = schemaName;
    }

    public AttributeNamingStrategy strategy() {
        return strategy;
    }

    public boolean hasStrategy() {
        return strategy != null;
    }

    /**
     * A strategy changes names when one is set and it is not {@link AttributeNamingStrategies#IDENTITY}. A custom
     * strategy counts as changing names, because schema build cannot prove that it returns its input.
     */
    public boolean changesNames() {
        return strategy != null && strategy != AttributeNamingStrategies.IDENTITY;
    }

    /**
     * Returns the stored name. When {@code literal} is true, or when no strategy is set, the input is stored as written.
     *
     * @param literalSource {@code @DynamoDbAttribute} or {@code storedName} when the name is literal, otherwise null
     */
    public String resolve(String input, boolean literal, String literalSource) {
        String storedName = literal || strategy == null ? input : strategy.transform(input);
        if (strategy != null && !literal) {
            rejectInvalidResult(input, storedName);
        }
        if (strategy != null) {
            rejectDuplicate(storedName, input, literal, literalSource);
            rejectPlaceholderClash(storedName);
        }
        return storedName;
    }

    /**
     * Records a stored name that a nested or extended schema already resolved. Used when a strategy is set, so a
     * flattened name participates in the duplicate and placeholder checks.
     */
    public void recordExternalStoredName(String storedName, String sourceDescription) {
        if (strategy == null) {
            return;
        }
        NameOrigin existing = storedNames.get(storedName);
        if (existing != null) {
            throw new IllegalArgumentException(schemaName + ": attribute name '" + storedName + "' from "
                                               + sourceDescription + " is already used.");
        }
        storedNames.put(storedName, new NameOrigin(sourceDescription, true, null));
        rejectPlaceholderClash(storedName);
    }

    private void rejectInvalidResult(String input, String storedName) {
        if (storedName == null) {
            throw new IllegalArgumentException("Attribute naming strategy returned null for '" + input + "' in "
                                               + schemaName);
        }
        if (storedName.isEmpty()) {
            throw new IllegalArgumentException("Attribute naming strategy returned an empty name for '" + input
                                               + "' in " + schemaName);
        }
    }

    private void rejectDuplicate(String storedName, String input, boolean literal, String literalSource) {
        NameOrigin existing = storedNames.get(storedName);
        if (existing != null) {
            throw new IllegalArgumentException(duplicateMessage(storedName, existing, input, literal, literalSource));
        }
        storedNames.put(storedName, new NameOrigin(input, literal, literalSource));
    }

    private void rejectPlaceholderClash(String storedName) {
        String placeholder = EnhancedClientUtils.keyRef(storedName);
        String previous = placeholderToStoredName.put(placeholder, storedName);
        if (previous != null && !previous.equals(storedName)) {
            throw new IllegalArgumentException(schemaName + ": " + strategy + " maps '" + previous + "' and '"
                                               + storedName + "' to the same expression placeholder '"
                                               + placeholder + "'.");
        }
    }

    private String duplicateMessage(String storedName,
                                    NameOrigin existing,
                                    String input,
                                    boolean literal,
                                    String literalSource) {
        if (literal && literalSource != null) {
            return schemaName + ": " + strategy + " maps '" + existing.input + "' to '" + storedName
                   + "', which " + literalSource + " also uses.";
        }
        if (existing.literal && existing.literalSource != null) {
            return schemaName + ": " + strategy + " maps '" + input + "' to '" + storedName
                   + "', which " + existing.literalSource + " also uses.";
        }
        return schemaName + ": " + strategy + " maps '" + existing.input + "' and '" + input
               + "' to the same attribute name '" + storedName + "'.";
    }

    private static final class NameOrigin {
        private final String input;
        private final boolean literal;
        private final String literalSource;

        private NameOrigin(String input, boolean literal, String literalSource) {
            this.input = input;
            this.literal = literal;
            this.literalSource = literalSource;
        }
    }
}

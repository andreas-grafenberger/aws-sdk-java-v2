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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;

public class AttributeNamingStrategiesTest {
    @Test
    public void snakeCase_frozenResults() {
        assertThat(transform(AttributeNamingStrategies.SNAKE_CASE, "httpRequest")).isEqualTo("http_request");
        assertThat(transform(AttributeNamingStrategies.SNAKE_CASE, "HTTPResponse")).isEqualTo("http_response");
        assertThat(transform(AttributeNamingStrategies.SNAKE_CASE, "PK")).isEqualTo("pk");
        assertThat(transform(AttributeNamingStrategies.SNAKE_CASE, "URLs")).isEqualTo("ur_ls");
        assertThat(transform(AttributeNamingStrategies.SNAKE_CASE, "address2Line")).isEqualTo("address2_line");
        assertThat(transform(AttributeNamingStrategies.SNAKE_CASE, "already_snake")).isEqualTo("already_snake");
        assertThat(transform(AttributeNamingStrategies.SNAKE_CASE, "_internalId")).isEqualTo("_internal_id");
        assertThat(transform(AttributeNamingStrategies.SNAKE_CASE, "customerID")).isEqualTo("customer_id");
        assertThat(transform(AttributeNamingStrategies.SNAKE_CASE, "customerIDCode")).isEqualTo("customer_id_code");
        assertThat(transform(AttributeNamingStrategies.SNAKE_CASE, "\u0130D")).isEqualTo("id");
    }

    @Test
    public void lowerCamelCase_changesOnlyTheFirstCharacter() {
        assertThat(transform(AttributeNamingStrategies.LOWER_CAMEL_CASE, "httpRequest")).isEqualTo("httpRequest");
        assertThat(transform(AttributeNamingStrategies.LOWER_CAMEL_CASE, "HTTPResponse")).isEqualTo("hTTPResponse");
        assertThat(transform(AttributeNamingStrategies.LOWER_CAMEL_CASE, "PK")).isEqualTo("pK");
        assertThat(transform(AttributeNamingStrategies.LOWER_CAMEL_CASE, "HttpRequest")).isEqualTo("httpRequest");
    }

    @Test
    public void upperCamelCase_changesOnlyTheFirstCharacter() {
        assertThat(transform(AttributeNamingStrategies.UPPER_CAMEL_CASE, "httpRequest")).isEqualTo("HttpRequest");
        assertThat(transform(AttributeNamingStrategies.UPPER_CAMEL_CASE, "HTTPResponse")).isEqualTo("HTTPResponse");
        assertThat(transform(AttributeNamingStrategies.UPPER_CAMEL_CASE, "PK")).isEqualTo("PK");
    }

    @Test
    public void identity_returnsTheInput() {
        assertThat(transform(AttributeNamingStrategies.IDENTITY, "httpRequest")).isEqualTo("httpRequest");
        assertThat(transform(AttributeNamingStrategies.IDENTITY, "PK")).isEqualTo("PK");
        assertThat(transform(AttributeNamingStrategies.IDENTITY, "URLs")).isEqualTo("URLs");
    }

    @Test
    public void builtInsIgnoreTheDefaultLocale() {
        String[] inputs = {"ID", "Id", "customerID", "IPAddress", "isActive", "URLs", "httpRequest", "\u0130D"};
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ROOT);
            Map<String, String> rootResults = results(inputs);
            Locale.setDefault(Locale.forLanguageTag("tr"));
            Map<String, String> turkishResults = results(inputs);
            assertThat(turkishResults).isEqualTo(rootResults);
        } finally {
            Locale.setDefault(original);
        }
    }

    private static Map<String, String> results(String[] inputs) {
        Map<String, String> results = new LinkedHashMap<>();
        for (String input : inputs) {
            results.put("snake:" + input, transform(AttributeNamingStrategies.SNAKE_CASE, input));
            results.put("lower:" + input, transform(AttributeNamingStrategies.LOWER_CAMEL_CASE, input));
            results.put("upper:" + input, transform(AttributeNamingStrategies.UPPER_CAMEL_CASE, input));
            results.put("identity:" + input, transform(AttributeNamingStrategies.IDENTITY, input));
        }
        return results;
    }

    private static String transform(AttributeNamingStrategy strategy, String input) {
        return strategy.transform(input);
    }
}

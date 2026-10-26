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

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AttributeNamingStrategiesTest {

    @ParameterizedTest
    @MethodSource("names")
    void transformsSourceName(AttributeNamingStrategy strategy, String sourceName, String storedName) {
        assertThat(strategy.transform(sourceName)).isEqualTo(storedName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"___", "---"})
    void rejectsSourceNameThatYieldsNoWord(String sourceName) {
        assertThatThrownBy(() -> AttributeNamingStrategies.LOWER_SNAKE_CASE.transform(sourceName))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Every row of the Appendix A comparison tables. Columns: source name, then the expected stored name for
     * LOWER_SNAKE_CASE, UPPER_SNAKE_CASE, LOWER_CAMEL_CASE, and UPPER_CAMEL_CASE.
     */
    private static final String[][] APPENDIX_A = {
        {"HTTPRequest", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {"HttpRequest", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {"httpRequest", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {"HTTPREQUEST", "httprequest", "HTTPREQUEST", "httprequest", "Httprequest"},
        {"httpREQUEST", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {"2HttpRequest", "2_http_request", "2_HTTP_REQUEST", "2HttpRequest", "2HttpRequest"},
        {"HTTP_REQUEST", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {"Http-Request", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {"Http.Request", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {"_HttpRequest", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {"httprequest", "httprequest", "HTTPREQUEST", "httprequest", "Httprequest"},
        {"Httprequest", "httprequest", "HTTPREQUEST", "httprequest", "Httprequest"},
        {"HTTPrequest", "htt_prequest", "HTT_PREQUEST", "httPrequest", "HttPrequest"},
        {"HttpRequest_2", "http_request_2", "HTTP_REQUEST_2", "httpRequest2", "HttpRequest2"},
        {"Http_Request", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {"-HttpRequest", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {"HttpRequest2", "http_request2", "HTTP_REQUEST2", "httpRequest2", "HttpRequest2"},
        {" HttpRequest", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {"HTTP Request", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {"Http Request", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {"http Request", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"},
        {" HTTP Request", "http_request", "HTTP_REQUEST", "httpRequest", "HttpRequest"}
    };

    private static Stream<Arguments> names() {
        AttributeNamingStrategy[] strategies = {
            AttributeNamingStrategies.LOWER_SNAKE_CASE,
            AttributeNamingStrategies.UPPER_SNAKE_CASE,
            AttributeNamingStrategies.LOWER_CAMEL_CASE,
            AttributeNamingStrategies.UPPER_CAMEL_CASE
        };
        Stream.Builder<Arguments> rows = Stream.builder();
        for (String[] row : APPENDIX_A) {
            for (int i = 0; i < strategies.length; i++) {
                rows.add(Arguments.of(strategies[i], row[0], row[i + 1]));
            }
        }
        return rows.build();
    }
}

/*
 * Copyright 2018 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.plugin.httpclient5;

import com.navercorp.pinpoint.bootstrap.plugin.request.util.CookieExtractor;
import com.navercorp.pinpoint.common.util.StringUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;

public class HttpClient5CookieExtractor implements CookieExtractor<HttpRequest> {

    public static final CookieExtractor<HttpRequest> INSTANCE = new HttpClient5CookieExtractor();

    @Override
    public String getCookie(HttpRequest cookie) {
        final Header[] cookies = cookie.getHeaders("Cookie");
        for (Header header : cookies) {
            final String value = header.getValue();
            if (StringUtils.hasLength(value)) {
                return value;
            }
        }
        return null;
    }
}

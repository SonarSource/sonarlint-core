/*
 * SonarLint Core - HTTP
 * Copyright (C) 2016-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.http;

import java.io.Closeable;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The client(IDE) is responsible to provide an HttpClient, configured with authentication, timeouts, proxy support, ...
 * @deprecated
 */
@Deprecated
public interface HttpClient {

  String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
  String FORM_URL_ENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";

  interface Response extends Closeable {

    int code();

    default boolean isSuccessful() {
      return code() >= 200 && code() < 300;
    }

    String bodyAsString();

    InputStream bodyAsStream();

    /**
     * Only runtime exception
     */
    @Override
    void close();

    String url();
  }

  Response get(String url);

  CompletableFuture<Response> getAsync(String url);

  AsyncRequest getEventStream(String url, HttpConnectionListener connectionListener, Consumer<String> messageConsumer);

  Response post(String url, String contentType, String body);

  default CompletableFuture<Response> postAsync(String url, String contentType, String body) {
    // default implementation to keep backward compatibility
    throw new UnsupportedOperationException();
  }

  Response delete(String url, String contentType, String body);

  interface AsyncRequest {
    void cancel();
  }

}

/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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

/**
 * The client(IDE) is responsible to provide an HttpClient, configured with authentication, timeouts, proxy support, ...
 */
public interface SonarLintHttpClient {

  String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

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

  Response post(String url, String contentType, String body);

  Response delete(String url, String contentType, String body);

}

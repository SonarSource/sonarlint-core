/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource SA
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
package testutils;

import mockwebserver3.MockResponse;
import okio.Buffer;

public class MockWebServerResponseBuilder {
  private int status = 200;
  private Buffer body;

  public static MockWebServerResponseBuilder newBuilder() {
    return new MockWebServerResponseBuilder();
  }

  public MockWebServerResponseBuilder setResponseCode(int status) {
    this.status = status;
    return this;
  }

  public MockWebServerResponseBuilder setBody(String body) {
    this.body = new Buffer().writeUtf8(body);
    return this;
  }

  public MockWebServerResponseBuilder setBody(Buffer body) {
    this.body = body;
    return this;
  }

  public MockResponse build() {
    var response = new MockResponse.Builder();
    response.setCode(status);
    if (body != null) {
      response.body(body);
    }
    return response.build();
  }
}

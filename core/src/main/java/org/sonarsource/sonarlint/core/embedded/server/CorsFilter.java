/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import java.io.IOException;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.io.HttpFilterHandler;
import org.apache.hc.core5.http.protocol.HttpContext;

class CorsFilter implements HttpFilterHandler {

  @Override
  public void handle(ClassicHttpRequest request, HttpFilterChain.ResponseTrigger responseTrigger, HttpContext context, HttpFilterChain chain)
    throws HttpException, IOException {
    var origin = request.getHeader("Origin");
    chain.proceed(request, new HttpFilterChain.ResponseTrigger() {
      @Override
      public void sendInformation(ClassicHttpResponse classicHttpResponse) throws HttpException, IOException {
        responseTrigger.sendInformation(classicHttpResponse);
      }

      @Override
      public void submitResponse(ClassicHttpResponse classicHttpResponse) throws HttpException, IOException {
        if (origin != null) {
          classicHttpResponse.addHeader("Access-Control-Allow-Origin", origin.getValue());
        }
        responseTrigger.submitResponse(classicHttpResponse);
      }
    }, context);
  }
}

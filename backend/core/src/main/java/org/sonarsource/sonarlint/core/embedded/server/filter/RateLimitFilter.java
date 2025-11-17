/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.embedded.server.filter;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.io.HttpFilterHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.embedded.server.AttributeUtils;

public class RateLimitFilter implements HttpFilterHandler {

  private static final int MAX_REQUESTS_PER_ORIGIN = 10;
  private static final long TIME_FRAME_MS = TimeUnit.SECONDS.toMillis(10);
  private final ConcurrentHashMap<String, RequestCounter> requestCounters = new ConcurrentHashMap<>();

  @Override
  public void handle(ClassicHttpRequest request, HttpFilterChain.ResponseTrigger responseTrigger, HttpContext context, HttpFilterChain chain)
    throws HttpException, IOException {
    var originHeader = request.getHeader("Origin");
    var origin = originHeader != null ? originHeader.getValue() : null;
    if (origin == null) {
      var response = new BasicClassicHttpResponse(HttpStatus.SC_BAD_REQUEST);
      responseTrigger.submitResponse(response);
    } else {
      if (!isRequestAllowed(origin)) {
        var response = new BasicClassicHttpResponse(HttpStatus.SC_TOO_MANY_REQUESTS);
        responseTrigger.submitResponse(response);
      } else {
        context.setAttribute(AttributeUtils.ORIGIN_ATTRIBUTE, origin);
        chain.proceed(request, responseTrigger, context);
      }
    }
  }

  private boolean isRequestAllowed(String origin) {
    long currentTime = System.currentTimeMillis();
    var counter = requestCounters.computeIfAbsent(origin, k -> new RequestCounter(currentTime));
    requestCounters.compute(origin, (k, v) -> {
      if (currentTime - counter.timestamp > TIME_FRAME_MS) {
        counter.timestamp = currentTime;
        counter.count = 1;
      } else {
        counter.count++;
      }
      return counter;
    });
    return counter.count <= MAX_REQUESTS_PER_ORIGIN;
  }

  private static class RequestCounter {
    long timestamp;
    int count;

    RequestCounter(long timestamp) {
      this.timestamp = timestamp;
      this.count = 0;
    }
  }

}

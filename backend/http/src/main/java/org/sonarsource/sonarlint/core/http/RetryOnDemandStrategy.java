/*
 * SonarLint Core - HTTP
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
package org.sonarsource.sonarlint.core.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLException;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

public class RetryOnDemandStrategy extends DefaultHttpRequestRetryStrategy {

  private static final List<Class<? extends IOException>> SAME_AS_DEFAULT = Arrays.asList(
    InterruptedIOException.class,
    UnknownHostException.class,
    ConnectException.class,
    ConnectionClosedException.class,
    NoRouteToHostException.class,
    SSLException.class);

  public RetryOnDemandStrategy(int maxRetries, TimeValue defaultRetryInterval) {
    super(maxRetries,
      defaultRetryInterval,
      SAME_AS_DEFAULT,
      Arrays.asList(
        HttpStatus.SC_TOO_MANY_REQUESTS,
        HttpStatus.SC_INTERNAL_SERVER_ERROR,
        HttpStatus.SC_SERVICE_UNAVAILABLE));
  }

  @Override
  public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
    return areRetriesEnabled(context)
      && super.retryRequest(response, execCount, context);
  }

  private static boolean areRetriesEnabled(HttpContext context) {
    var retriesEnabledFlag = context.getAttribute(ContextAttributes.RETRIES_ENABLED);
    return Boolean.TRUE.equals(retriesEnabledFlag);
  }
}

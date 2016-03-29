/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import org.sonarqube.ws.client.WsResponse;

public class CloseableWsResponse implements WsResponse, AutoCloseable {

  private final WsResponse delegate;

  public CloseableWsResponse(WsResponse delegate) {
    this.delegate = delegate;
  }

  @Override
  public void close() {
    try {
      delegate.contentStream().close();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to properly close response", e);
    }
  }

  @Override
  public String requestUrl() {
    return delegate.requestUrl();
  }

  @Override
  public int code() {
    return delegate.code();
  }

  @Override
  public boolean isSuccessful() {
    return delegate.isSuccessful();
  }

  @Override
  public WsResponse failIfNotSuccessful() {
    return delegate.failIfNotSuccessful();
  }

  @Override
  public String contentType() {
    return delegate.contentType();
  }

  @Override
  public boolean hasContent() {
    return delegate.hasContent();
  }

  @Override
  public InputStream contentStream() {
    return delegate.contentStream();
  }

  @Override
  public Reader contentReader() {
    return delegate.contentReader();
  }

  @Override
  public String content() {
    return delegate.content();
  }

}

/*
 * SonarLint Core - Backend CLI
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
package org.sonarsource.sonarlint.core.backend.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public class EndOfStreamAwareInputStream extends InputStream {
  private final InputStream delegate;
  private final CompletableFuture<Void> onExit = new CompletableFuture<>();

  public EndOfStreamAwareInputStream(InputStream delegate) {
    this.delegate = delegate;
  }

  public CompletableFuture<Void> onExit() {
    return onExit;
  }

  @Override
  public int read() throws IOException {
    return exitIfNegative(delegate::read);
  }

  @Override
  public int read(byte[] b) throws IOException {
    return exitIfNegative(() -> delegate.read(b));
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return exitIfNegative(() -> delegate.read(b, off, len));
  }

  private int exitIfNegative(SupplierWithIOException<Integer> call) throws IOException {
    int result = call.get();

    if (result < 0) {
      onExit.complete(null);
    }

    return result;
  }

  @FunctionalInterface
  private interface SupplierWithIOException<T> {
    /**
     * @return result
     */
    T get() throws IOException;
  }
}

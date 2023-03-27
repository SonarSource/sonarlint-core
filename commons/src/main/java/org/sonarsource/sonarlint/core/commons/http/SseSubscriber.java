/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.http;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

/**
 * Inspired by https://stackoverflow.com/questions/63500818/sse-java-11-client-example-w-o-dependencies
 */
public class SseSubscriber implements HttpResponse.BodySubscriber<Void> {
  protected final Consumer<? super String> messageConsumer;
  protected final CompletableFuture<Void> future;
  protected Flow.Subscription subscription;
  protected volatile String deferredText;

  public SseSubscriber(Consumer<? super String> messageConsumer) {
    this.messageConsumer = messageConsumer;
    this.future = new CompletableFuture<>();
    this.subscription = null;
    this.deferredText = null;
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    try {
      this.deferredText = "";
      this.subscription.request(1);
    } catch (Exception e) {
      this.future.completeExceptionally(e);
      this.subscription.cancel();
    }
  }

  @Override
  public void onNext(List<ByteBuffer> buffers) {
    try {
      for (var buffer : buffers) {
        // TODO: Safe to assume multi-byte chars don't get split across buffers?
        this.messageConsumer.accept(StandardCharsets.UTF_8.decode(buffer).toString());
      }
      this.subscription.request(1);
    } catch (Exception e) {
      this.future.completeExceptionally(e);
      this.subscription.cancel();
    }
  }

  @Override
  public void onError(Throwable e) {
    this.future.completeExceptionally(e);
  }

  @Override
  public void onComplete() {
    try {
      this.future.complete(null);
    } catch (Exception e) {
      this.future.completeExceptionally(e);
    }
  }

  @Override
  public CompletionStage<Void> getBody() {
    return this.future;
  }
}

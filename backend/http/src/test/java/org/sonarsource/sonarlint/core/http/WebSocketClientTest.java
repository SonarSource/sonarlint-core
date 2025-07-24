/*
 * SonarLint Core - HTTP
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
package org.sonarsource.sonarlint.core.http;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSocketClientTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static ExecutorService executor;

  @BeforeAll
  static void setUp() {
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterAll
  static void tearDown() {
    if (executor != null) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  @Test
  void should_validate_null_uri() {
    var client = new WebSocketClient("test-agent", "token", executor);
    
    var future = client.createWebSocketConnection(null, message -> {}, () -> {});
    
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
      .hasCauseInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("WebSocket URI must use 'ws' or 'wss' scheme");
  }

  @Test
  void should_validate_invalid_scheme() {
    var client = new WebSocketClient("test-agent", "token", executor);
    
    var future = client.createWebSocketConnection(URI.create("http://example.com"), message -> {}, () -> {});
    
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
      .hasCauseInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("WebSocket URI must use 'ws' or 'wss' scheme");
  }

  @Test
  void should_accept_valid_ws_uri() {
    var client = new WebSocketClient("test-agent", "token", executor);
    
    var future = client.createWebSocketConnection(URI.create("ws://example.com"), message -> {}, () -> {});

    assertThat(future).isNotCompletedExceptionally();
  }

  @Test
  void should_accept_valid_wss_uri() {
    var client = new WebSocketClient("test-agent", "token", executor);
    
    var future = client.createWebSocketConnection(URI.create("wss://example.com"), message -> {}, () -> {});

    assertThat(future).isNotCompletedExceptionally();
  }

}

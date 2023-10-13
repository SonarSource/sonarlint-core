/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import org.eclipse.lsp4j.jsonrpc.JsonRpcException;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.MessageIssueException;
import org.eclipse.lsp4j.jsonrpc.messages.Message;

/**
 * Workaround for PipedInputStream not liking to be fed from multiple threads. We are wrapping the lsp4j {@link org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer}
 * with our own implementation that uses a queue and a single thread to consume messages.
 */
public class SingleThreadedMessageConsumer implements MessageConsumer {

  private final LinkedBlockingQueue<Message> queue = new LinkedBlockingQueue<>();

  public SingleThreadedMessageConsumer(MessageConsumer syncMessageConsumer, ExecutorService threadPool) {
    threadPool.submit(() -> {
      while (true) {
        Message message;
        try {
          message = queue.take();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        try {
          syncMessageConsumer.consume(message);
        } catch (Exception e) {
          System.err.println("Error while consuming message");
          e.printStackTrace(System.err);
        }
      }
    });
  }

  @Override
  public void consume(Message message) throws MessageIssueException, JsonRpcException {
    queue.add(message);
  }
}

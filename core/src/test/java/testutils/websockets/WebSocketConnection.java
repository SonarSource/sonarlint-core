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
package testutils.websockets;

import jakarta.websocket.Session;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.CheckForNull;

public class WebSocketConnection {
  private final String authorizationHeader;
  private boolean isOpened;
  private final List<String> receivedMessages = new CopyOnWriteArrayList<>();
  private final Queue<String> preparedAnswers = new LinkedList<>();
  private Throwable throwable;
  private Session session;

  public WebSocketConnection(String authorizationHeader) {
    this.authorizationHeader = authorizationHeader;
  }

  public void setSession(Session session) {
    this.session = session;
    setIsOpened();
  }

  public String getAuthorizationHeader() {
    return authorizationHeader;
  }

  public void setIsOpened() {
    isOpened = true;
  }

  public boolean isOpened() {
    return isOpened;
  }

  public List<String> getReceivedMessages() {
    return receivedMessages;
  }

  public void addReceivedMessage(String message) {
    receivedMessages.add(message);
  }

  void setIsClosed() {
    isOpened = false;
  }

  public void setIsError(Throwable throwable) {
    this.throwable = throwable;
  }

  @CheckForNull
  public Throwable getThrowable() {
    return throwable;
  }

  public void addPreparedAnswer(String answer) {
    preparedAnswers.offer(answer);
  }

  public void sendMessage(String message) {
    if (session != null) {
      try {
        session.getBasicRemote().sendText(message);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @CheckForNull
  public String pollPreparedAnswer() {
    return preparedAnswers.poll();
  }

  public void close() {
    if (session != null) {
      try {
        session.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}

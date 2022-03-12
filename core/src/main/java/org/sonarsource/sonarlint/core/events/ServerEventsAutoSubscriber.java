/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.events;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.stream.EventStream;

public class ServerEventsAutoSubscriber {

  private final ServerEventHandler eventHandler;
  private final AtomicReference<EventStream> eventStream = new AtomicReference<>();

  public ServerEventsAutoSubscriber(ServerEventHandler<?> eventHandler) {
    this.eventHandler = eventHandler;
  }

  public void subscribePermanently(ServerApi serverApi, Set<String> projectKeys, Set<Language> enabledLanguages, ClientLogOutput clientLogOutput) {
    cancelSubscription();
    if (!projectKeys.isEmpty() && !enabledLanguages.isEmpty()) {
      attemptSubscription(serverApi, projectKeys, enabledLanguages, clientLogOutput);
    }
  }

  private void attemptSubscription(ServerApi serverApi, Set<String> projectKeys, Set<Language> enabledLanguages, ClientLogOutput clientLogOutput) {
    eventStream.set(serverApi.push().subscribe(projectKeys, enabledLanguages, eventHandler::handle, clientLogOutput));
  }

  public void cancelSubscription() {
    if (eventStream.get() != null) {
      eventStream.get().close();
    }
  }

  public void stop() {
    cancelSubscription();
  }
}

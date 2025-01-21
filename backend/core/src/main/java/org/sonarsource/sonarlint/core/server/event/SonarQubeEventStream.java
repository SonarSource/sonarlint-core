/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.server.event;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.ConnectionManager;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.serverapi.push.SonarServerEvent;
import org.sonarsource.sonarlint.core.serverapi.stream.EventStream;

public class SonarQubeEventStream {
  private EventStream eventStream;
  private final Set<String> subscribedProjectKeys = new LinkedHashSet<>();
  private final Set<SonarLanguage> enabledLanguages;
  private final String connectionId;
  private final ConnectionManager connectionManager;
  private final Consumer<SonarServerEvent> eventConsumer;

  public SonarQubeEventStream(Set<SonarLanguage> enabledLanguages, String connectionId, ConnectionManager connectionManager, Consumer<SonarServerEvent> eventConsumer) {
    this.enabledLanguages = enabledLanguages;
    this.connectionId = connectionId;
    this.connectionManager = connectionManager;
    this.eventConsumer = eventConsumer;
  }

  public synchronized void subscribeNew(Set<String> possiblyNewProjectKeys) {
    if (!possiblyNewProjectKeys.isEmpty() && !subscribedProjectKeys.containsAll(possiblyNewProjectKeys)) {
      cancelSubscription();
      subscribedProjectKeys.addAll(possiblyNewProjectKeys);
      attemptSubscription(subscribedProjectKeys);
    }
  }

  public synchronized void resubscribe() {
    cancelSubscription();
    if (!subscribedProjectKeys.isEmpty()) {
      attemptSubscription(subscribedProjectKeys);
    }
  }

  public synchronized void unsubscribe(String projectKey) {
    cancelSubscription();
    subscribedProjectKeys.remove(projectKey);
    if (!subscribedProjectKeys.isEmpty()) {
      attemptSubscription(subscribedProjectKeys);
    }
  }

  private void attemptSubscription(Set<String> projectKeys) {
    if (!enabledLanguages.isEmpty()) {
      connectionManager.getServerApi(connectionId)
        .ifPresent(serverApi -> eventStream = serverApi.push().subscribe(projectKeys, enabledLanguages, e -> notifyHandlers(e, eventConsumer)));
    }
  }

  private static void notifyHandlers(SonarServerEvent sonarServerEvent, Consumer<SonarServerEvent> clientEventConsumer) {
    clientEventConsumer.accept(sonarServerEvent);
  }

  private void cancelSubscription() {
    if (eventStream != null) {
      eventStream.close();
      eventStream = null;
    }
  }

  public synchronized void stop() {
    subscribedProjectKeys.clear();
    cancelSubscription();
  }

}

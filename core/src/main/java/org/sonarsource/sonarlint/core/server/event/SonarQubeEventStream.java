/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.server.event;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.push.ServerEvent;
import org.sonarsource.sonarlint.core.serverapi.stream.EventStream;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.events.EventDispatcher;
import org.sonarsource.sonarlint.core.serverconnection.events.ServerEventsAutoSubscriber;

public class SonarQubeEventStream {
  private EventStream eventStream;
  private final Set<String> subscribedProjectKeys = new LinkedHashSet<>();
  private final EventDispatcher coreEventRouter;
  private final Set<Language> enabledLanguages;
  private final String connectionId;
  private final ServerApiProvider serverApiProvider;
  private final Consumer<ServerEvent> eventConsumer;

  public SonarQubeEventStream(ConnectionStorage storage, Set<Language> enabledLanguages, String connectionId, ServerApiProvider serverApiProvider,
    Consumer<ServerEvent> eventConsumer) {
    coreEventRouter = ServerEventsAutoSubscriber.getCoreEventHandlers(storage);
    this.enabledLanguages = enabledLanguages;
    this.connectionId = connectionId;
    this.serverApiProvider = serverApiProvider;
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
      serverApiProvider.getServerApi(connectionId)
        .ifPresent(serverApi -> eventStream = serverApi.push().subscribe(projectKeys, enabledLanguages, e -> notifyHandlers(e, eventConsumer)));
    }
  }

  private void notifyHandlers(ServerEvent serverEvent, Consumer<ServerEvent> clientEventConsumer) {
    coreEventRouter.handle(serverEvent);
    clientEventConsumer.accept(serverEvent);
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

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
package org.sonarsource.sonarlint.core.server.event;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.push.ServerEvent;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.RuleSetChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotRaisedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.serverapi.stream.EventStream;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.events.EventDispatcher;
import org.sonarsource.sonarlint.core.serverconnection.events.hotspot.UpdateStorageOnSecurityHotspotChanged;
import org.sonarsource.sonarlint.core.serverconnection.events.hotspot.UpdateStorageOnSecurityHotspotClosed;
import org.sonarsource.sonarlint.core.serverconnection.events.hotspot.UpdateStorageOnSecurityHotspotRaised;
import org.sonarsource.sonarlint.core.serverconnection.events.issue.UpdateStorageOnIssueChanged;
import org.sonarsource.sonarlint.core.serverconnection.events.ruleset.UpdateStorageOnRuleSetChanged;
import org.sonarsource.sonarlint.core.serverconnection.events.taint.UpdateStorageOnTaintVulnerabilityClosed;
import org.sonarsource.sonarlint.core.serverconnection.events.taint.UpdateStorageOnTaintVulnerabilityRaised;

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
    coreEventRouter = new EventDispatcher()
      .dispatch(RuleSetChangedEvent.class, new UpdateStorageOnRuleSetChanged(storage))
      .dispatch(IssueChangedEvent.class, new UpdateStorageOnIssueChanged(storage))
      .dispatch(TaintVulnerabilityRaisedEvent.class, new UpdateStorageOnTaintVulnerabilityRaised(storage))
      .dispatch(TaintVulnerabilityClosedEvent.class, new UpdateStorageOnTaintVulnerabilityClosed(storage))
      .dispatch(SecurityHotspotRaisedEvent.class, new UpdateStorageOnSecurityHotspotRaised(storage))
      .dispatch(SecurityHotspotChangedEvent.class, new UpdateStorageOnSecurityHotspotChanged(storage))
      .dispatch(SecurityHotspotClosedEvent.class, new UpdateStorageOnSecurityHotspotClosed(storage));
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

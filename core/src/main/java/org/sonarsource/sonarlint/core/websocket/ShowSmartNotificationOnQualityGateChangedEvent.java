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
package org.sonarsource.sonarlint.core.websocket;

import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.serverconnection.events.ServerEventHandler;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;
import org.sonarsource.sonarlint.core.websocket.events.QualityGateChangedEvent;

public class ShowSmartNotificationOnQualityGateChangedEvent implements ServerEventHandler<QualityGateChangedEvent> {
  public static final String NOTIFICATION_CATEGORY = "QUALITY_GATE";
  private final SonarLintClient client;
  private final ConfigurationRepository configurationRepository;
  private final TelemetryServiceImpl telemetryService;

  public ShowSmartNotificationOnQualityGateChangedEvent(SonarLintClient client, ConfigurationRepository configurationRepository, TelemetryServiceImpl telemetryService) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.telemetryService = telemetryService;
  }

  @Override
  public void handle(QualityGateChangedEvent event) {
    var projectKey = event.getProject();
    configurationRepository.getBoundScopesByProject(projectKey).stream()
      .collect(Collectors.groupingBy(BoundScope::getConnectionId))
      .forEach((connectionId, scope) -> client.showSmartNotification(new ShowSmartNotificationParams(event.getMessage(), event.getLink(),
        scope.stream().map(BoundScope::getId).collect(Collectors.toSet()), NOTIFICATION_CATEGORY, connectionId)));
    telemetryService.smartNotificationsReceived(NOTIFICATION_CATEGORY);
  }
}

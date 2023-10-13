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
package org.sonarsource.sonarlint.core.event;

<<<<<<<< HEAD:core/src/main/java/org/sonarsource/sonarlint/core/event/SonarServerEventReceivedEvent.java
import org.sonarsource.sonarlint.core.serverapi.push.SonarServerEvent;

public class SonarServerEventReceivedEvent {
  private final String connectionId;
  private final SonarServerEvent event;

  public SonarServerEventReceivedEvent(String connectionId, SonarServerEvent event) {
    this.connectionId = connectionId;
    this.event = event;
========
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class DidReceiveServerTaintVulnerabilityChangedOrClosedEvent {

  @NonNull
  private final String connectionId;
  private final String sonarProjectKey;

  public DidReceiveServerTaintVulnerabilityChangedOrClosedEvent(String connectionId, String sonarProjectKey) {
    this.connectionId = connectionId;
    this.sonarProjectKey = sonarProjectKey;
>>>>>>>> c02a9742b (SLCORE-571 Make the client-api JSON-RPC friendly (#738)):client-api/src/main/java/org/sonarsource/sonarlint/core/clientapi/client/event/DidReceiveServerTaintVulnerabilityChangedOrClosedEvent.java
  }

  public String getConnectionId() {
    return connectionId;
  }

<<<<<<<< HEAD:core/src/main/java/org/sonarsource/sonarlint/core/event/SonarServerEventReceivedEvent.java
  public SonarServerEvent getEvent() {
    return event;
========
  public String getSonarProjectKey() {
    return sonarProjectKey;
>>>>>>>> c02a9742b (SLCORE-571 Make the client-api JSON-RPC friendly (#738)):client-api/src/main/java/org/sonarsource/sonarlint/core/clientapi/client/event/DidReceiveServerTaintVulnerabilityChangedOrClosedEvent.java
  }
}

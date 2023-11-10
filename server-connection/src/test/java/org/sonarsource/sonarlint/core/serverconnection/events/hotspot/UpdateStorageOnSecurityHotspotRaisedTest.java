/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.events.hotspot;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotRaisedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.SonarProjectStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectServerIssueStore;
import testutils.InMemoryIssueStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateStorageOnSecurityHotspotRaisedTest {
  private static final String PROJECT_KEY = "projectKey";
  private ProjectServerIssueStore serverIssueStore;
  private UpdateStorageOnSecurityHotspotRaised handler;

  @BeforeEach
  void setUp() {
    serverIssueStore = new InMemoryIssueStore();
    ConnectionStorage storage = mock(ConnectionStorage.class);
    var projectStorage = mock(SonarProjectStorage.class);
    when(storage.project(PROJECT_KEY)).thenReturn(projectStorage);
    when(projectStorage.findings()).thenReturn(serverIssueStore);
    handler = new UpdateStorageOnSecurityHotspotRaised(storage);
  }

  @Test
  void should_store_hotspot() {
    handler.handle(new SecurityHotspotRaisedEvent(
      "hotspotKey",
      PROJECT_KEY,
      VulnerabilityProbability.LOW,
      HotspotReviewStatus.TO_REVIEW,
      Instant.now(),
      "myBranch",
      new TaintVulnerabilityRaisedEvent.Location(
        "filePath",
        "message",
        new TaintVulnerabilityRaisedEvent.Location.TextRange(
          1, 2, 3, 4, "hash")),
      "ruleKey",
      "context", "assignedPerson"));

    assertThat(serverIssueStore.loadHotspots("myBranch", "filePath"))
      .extracting(ServerHotspot::getKey)
      .containsOnly("hotspotKey");
    assertThat(serverIssueStore.loadHotspots("myBranch", "filePath"))
      .extracting(ServerHotspot::getMessage).containsExactly("message");
  }
}

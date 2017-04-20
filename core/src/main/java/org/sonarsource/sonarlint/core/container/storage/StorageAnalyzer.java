/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.storage;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectId;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.analysis.AnalysisContainer;
import org.sonarsource.sonarlint.core.container.connected.DefaultServer;
import org.sonarsource.sonarlint.core.container.model.DefaultAnalysisResult;

public class StorageAnalyzer {
  private final ProjectStorageStatusReader projectUpdateStatusReader;
  private final StorageManager storageManager;

  public StorageAnalyzer(StorageManager storageManager, ProjectStorageStatusReader moduleUpdateStatusReader) {
    this.storageManager = storageManager;
    this.projectUpdateStatusReader = moduleUpdateStatusReader;
  }

  private void checkStatus(@Nullable ProjectId projectId) {
    GlobalStorageStatus updateStatus = storageManager.getGlobalStorageStatus();
    if (updateStatus == null) {
      throw new StorageException("Missing global storage. Please update server.", false);
    }
    if (projectId != null) {

      ProjectStorageStatus projectUpdateStatus = projectUpdateStatusReader.readStatus(projectId);
      if (projectUpdateStatus == null) {
        throw new StorageException(String.format("No data stored for module '%s'. Please update the binding.", projectId), false);
      } else if (projectUpdateStatus.isStale()) {
        throw new StorageException(String.format("Stored data for module '%s' is stale because "
          + "it was created with a different version of SonarLint. Please update the binding.", projectId), false);
      }
    }
  }

  public AnalysisResults analyze(StorageContainer container, ConnectedAnalysisConfiguration configuration, IssueListener issueListener) {
    checkStatus(configuration.projectId());

    AnalysisContainer analysisContainer = new AnalysisContainer(container);
    DefaultAnalysisResult defaultAnalysisResult = new DefaultAnalysisResult();

    analysisContainer.add(
      configuration,
      issueListener,
      new SonarQubeActiveRulesProvider(),
      DefaultServer.class,
      defaultAnalysisResult);

    analysisContainer.execute();
    return defaultAnalysisResult;
  }
}

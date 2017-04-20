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
package org.sonarsource.sonarlint.core.container.connected.update.perform;

import java.nio.file.Path;
import java.util.Date;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.QualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.RulesDownloader;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class OrganizationStorageUpdateExecutor {

  private final StorageManager storageManager;
  private final RulesDownloader rulesDownloader;
  private final TempFolder tempFolder;
  private final SonarLintWsClient wsClient;
  private final QualityProfilesDownloader qualityProfilesDownloader;

  public OrganizationStorageUpdateExecutor(StorageManager storageManager, SonarLintWsClient wsClient,
    RulesDownloader rulesDownloader, QualityProfilesDownloader qualityProfilesDownloader, TempFolder tempFolder) {
    this.storageManager = storageManager;
    this.wsClient = wsClient;
    this.rulesDownloader = rulesDownloader;
    this.qualityProfilesDownloader = qualityProfilesDownloader;
    this.tempFolder = tempFolder;
  }

  public void update(String organizationKey, ProgressWrapper progress) {
    Path temp = tempFolder.newDir().toPath();

    try {
      progress.setProgressAndCheckCancel("Fetching rules", 0.4f);
      rulesDownloader.fetchRulesTo(temp);

      progress.setProgressAndCheckCancel("Fetching quality profiles", 0.4f);
      qualityProfilesDownloader.fetchQualityProfilesTo(temp);

      progress.startNonCancelableSection();
      progress.setProgressAndCheckCancel("Finalizing...", 1.0f);

      StorageStatus storageStatus = StorageStatus.newBuilder()
        .setStorageVersion(StorageManager.STORAGE_VERSION)
        .setClientUserAgent(wsClient.getUserAgent())
        .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
        .setUpdateTimestamp(new Date().getTime())
        .build();
      ProtobufUtil.writeToFile(storageStatus, temp.resolve(StorageManager.STORAGE_STATUS_PB));

      Path dest = storageManager.getOrganizationStorageRoot(organizationKey);
      FileUtils.deleteRecursively(dest);
      FileUtils.mkdirs(dest.getParent());
      FileUtils.moveDir(temp, dest);
    } catch (RuntimeException e) {
      try {
        FileUtils.deleteRecursively(temp);
      } catch (RuntimeException ignore) {
        // ignore because we want to throw original exception
      }
      throw e;
    }
  }
}

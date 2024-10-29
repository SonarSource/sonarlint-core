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
package org.sonarsource.sonarlint.core.mode;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.storage.StorageService;

@Named
@Singleton
public class SeverityModeService {

  private static final String MIN_MQR_MODE_SUPPORT_VERSION = "10.2";

  private final StorageService storageService;
  private final ServerApiProvider serverApiProvider;

  public SeverityModeService(StorageService storageService, ServerApiProvider serverApiProvider) {
    this.storageService = storageService;
    this.serverApiProvider = serverApiProvider;
  }

  public boolean isMQRModeForConnection(@Nullable String connectionId) {
    if (connectionId == null) {
      return true;
    }
    var isSonarCloud = serverApiProvider.getServerApiOrThrow(connectionId).isSonarCloud();
    if (isSonarCloud) {
      return true;
    }
    var optServerInfo = storageService.connection(connectionId).serverInfo().read();
    var isMQRMode = optServerInfo.map(StoredServerInfo::getSeverityMode).orElse(StoredServerInfo.SeverityModeDetails.MQR);
    if (isMQRMode == StoredServerInfo.SeverityModeDetails.DEFAULT) {
      return optServerInfo.get().getVersion().compareToIgnoreQualifier(Version.create(MIN_MQR_MODE_SUPPORT_VERSION)) >= 0;
    }
    return isMQRMode == StoredServerInfo.SeverityModeDetails.MQR;
  }

}

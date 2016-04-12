/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected.update;

import org.sonarqube.ws.QualityProfiles;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse.QualityProfile;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles.QProfile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class QualityProfilesDownloader {
  private static final String DEFAULT_QP_SEARCH_URL = "/api/qualityprofiles/search.protobuf";
  private final SonarLintWsClient wsClient;

  public QualityProfilesDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public void fetchQualityProfiles(Path destDir) {
    QProfiles.Builder qProfileBuilder = QProfiles.newBuilder();

    try (InputStream contentStream = wsClient.get(DEFAULT_QP_SEARCH_URL).contentStream()) {
      SearchWsResponse qpResponse = QualityProfiles.SearchWsResponse.parseFrom(contentStream);
      for (QualityProfile qp : qpResponse.getProfilesList()) {
        org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles.QProfile.Builder qpBuilder = QProfile.newBuilder();
        qpBuilder.setKey(qp.getKey());
        qpBuilder.setActiveRuleCount(qp.getActiveRuleCount());

        qProfileBuilder.getMutableQprofilesByKey().put(qp.getKey(), qpBuilder.build());
        if (qp.getIsDefault()) {
          qProfileBuilder.getMutableDefaultQProfilesByLanguage().put(qp.getLanguage(), qp.getKey());
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load default quality profiles", e);
    }

    ProtobufUtil.writeToFile(qProfileBuilder.build(), destDir.resolve(StorageManager.QUALITY_PROFILES_PB));
  }
}

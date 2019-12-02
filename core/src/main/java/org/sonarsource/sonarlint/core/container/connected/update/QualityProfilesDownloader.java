/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.nio.file.Path;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.QualityProfiles;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse.QualityProfile;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles.QProfile;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class QualityProfilesDownloader {
  private static final Logger LOG = Loggers.get(QualityProfilesDownloader.class);

  private static final String DEFAULT_QP_SEARCH_URL = "/api/qualityprofiles/search.protobuf";
  private final SonarLintWsClient wsClient;

  public QualityProfilesDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public void fetchQualityProfilesTo(Path destDir) {
    ProtobufUtil.writeToFile(fetchQualityProfiles(), destDir.resolve(StoragePaths.QUALITY_PROFILES_PB));
  }

  public QProfiles fetchQualityProfiles() {
    QProfiles.Builder qProfileBuilder = QProfiles.newBuilder();

    StringBuilder searchUrl = new StringBuilder();
    searchUrl.append(DEFAULT_QP_SEARCH_URL);
    wsClient.getOrganizationKey()
      .ifPresent(org -> searchUrl.append("?organization=").append(StringUtils.urlEncode(org)));
    SonarLintWsClient.consumeTimed(
      () -> wsClient.get(searchUrl.toString()),
      response -> {
        SearchWsResponse qpResponse = QualityProfiles.SearchWsResponse.parseFrom(response.contentStream());
        for (QualityProfile qp : qpResponse.getProfilesList()) {
          QProfile.Builder qpBuilder = QProfile.newBuilder();
          qpBuilder.setKey(qp.getKey());
          qpBuilder.setName(qp.getName());
          qpBuilder.setLanguage(qp.getLanguage());
          qpBuilder.setLanguageName(qp.getLanguageName());
          qpBuilder.setActiveRuleCount(qp.getActiveRuleCount());
          qpBuilder.setRulesUpdatedAt(qp.getRulesUpdatedAt());
          qpBuilder.setUserUpdatedAt(qp.getUserUpdatedAt());

          qProfileBuilder.putQprofilesByKey(qp.getKey(), qpBuilder.build());
          if (qp.getIsDefault()) {
            qProfileBuilder.putDefaultQProfilesByLanguage(qp.getLanguage(), qp.getKey());
          }
        }
      },
      duration -> LOG.debug("Downloaded quality profiles in {}ms", duration));

    return qProfileBuilder.build();
  }
}

/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.qualityprofile;

import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.Qualityprofiles;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class QualityProfileApi {
  private static final Logger LOG = Loggers.get(QualityProfileApi.class);
  private static final String DEFAULT_QP_SEARCH_URL = "/api/qualityprofiles/search.protobuf";

  private final ServerApiHelper helper;

  public QualityProfileApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public Sonarlint.QProfiles getQualityProfiles() {
    Sonarlint.QProfiles.Builder qProfileBuilder = Sonarlint.QProfiles.newBuilder();

    StringBuilder searchUrl = new StringBuilder();
    searchUrl.append(DEFAULT_QP_SEARCH_URL);
    helper.getOrganizationKey()
      .ifPresent(org -> searchUrl.append("?organization=").append(StringUtils.urlEncode(org)));
    ServerApiHelper.consumeTimed(
      () -> helper.get(searchUrl.toString()),
      response -> {
        Qualityprofiles.SearchWsResponse qpResponse = Qualityprofiles.SearchWsResponse.parseFrom(response.bodyAsStream());
        for (Qualityprofiles.SearchWsResponse.QualityProfile qp : qpResponse.getProfilesList()) {
          Sonarlint.QProfiles.QProfile.Builder qpBuilder = Sonarlint.QProfiles.QProfile.newBuilder();
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

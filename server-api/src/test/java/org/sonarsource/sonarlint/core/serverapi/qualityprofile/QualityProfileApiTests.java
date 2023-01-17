/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.qualityprofile;

import mockwebserver3.MockResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.exception.ProjectNotFoundException;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerErrorException;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Qualityprofiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QualityProfileApiTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  @Test
  void should_throw_when_the_endpoint_is_not_found() {
    var underTest = new QualityProfileApi(mockServer.serverApiHelper());

    mockServer.addResponse("/api/qualityprofiles/search.protobuf?project=projectKey", new MockResponse().setResponseCode(404));

    assertThrows(ProjectNotFoundException.class, () -> underTest.getQualityProfiles("projectKey"));
  }

  @Test
  void should_throw_when_a_server_error_occurs() {
    var underTest = new QualityProfileApi(mockServer.serverApiHelper());

    mockServer.addResponse("/api/qualityprofiles/search.protobuf?project=projectKey", new MockResponse().setResponseCode(503));

    assertThrows(ServerErrorException.class, () -> underTest.getQualityProfiles("projectKey"));
  }

  @Test
  void should_return_the_quality_profiles_of_a_given_project() {
    var underTest = new QualityProfileApi(mockServer.serverApiHelper());

    mockServer.addProtobufResponse("/api/qualityprofiles/search.protobuf?project=projectKey", Qualityprofiles.SearchWsResponse.newBuilder()
      .addProfiles(Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder()
        .setIsDefault(true)
        .setKey("profileKey")
        .setName("profileName")
        .setLanguage("lang")
        .setLanguageName("langName")
        .setActiveRuleCount(12)
        .setRulesUpdatedAt("rulesUpdatedAt")
        .setUserUpdatedAt("userUpdatedAt")
        .build())
      .build());

    var qualityProfiles = underTest.getQualityProfiles("projectKey");

    assertThat(qualityProfiles)
      .extracting("default", "key", "name", "language", "languageName", "activeRuleCount", "rulesUpdatedAt", "userUpdatedAt")
      .containsOnly(tuple(true, "profileKey", "profileName", "lang", "langName", 12L, "rulesUpdatedAt", "userUpdatedAt"));

  }
}

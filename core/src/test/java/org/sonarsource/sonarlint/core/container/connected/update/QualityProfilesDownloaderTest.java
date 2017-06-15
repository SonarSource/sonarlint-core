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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

public class QualityProfilesDownloaderTest {
  private QualityProfilesDownloader qProfilesDownloader;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void test() {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithStreamResponse("/api/qualityprofiles/search.protobuf", "/update/qualityprofiles.pb");
    qProfilesDownloader = new QualityProfilesDownloader(wsClient);
    qProfilesDownloader.fetchQualityProfilesTo(temp.getRoot().toPath());

    QProfiles qProfiles = ProtobufUtil.readFile(temp.getRoot().toPath().resolve(StoragePaths.QUALITY_PROFILES_PB), QProfiles.parser());
    assertThat(qProfiles.getQprofilesByKeyMap()).containsOnlyKeys(
      "cs-sonar-way-58886",
      "java-sonar-way-74592",
      "java-empty-74333",
      "js-sonar-security-way-70539",
      "js-sonar-way-60746");

    assertThat(qProfiles.getDefaultQProfilesByLanguageMap()).containsOnly(
      entry("cs", "cs-sonar-way-58886"),
      entry("java", "java-sonar-way-74592"),
      entry("js", "js-sonar-way-60746"));
  }

  @Test
  public void testWithOrg() {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithStreamResponse("/api/qualityprofiles/search.protobuf?organization=myOrg", "/update/qualityprofiles.pb");
    when(wsClient.getOrganizationKey()).thenReturn("myOrg");
    qProfilesDownloader = new QualityProfilesDownloader(wsClient);
    qProfilesDownloader.fetchQualityProfilesTo(temp.getRoot().toPath());

    QProfiles qProfiles = ProtobufUtil.readFile(temp.getRoot().toPath().resolve(StoragePaths.QUALITY_PROFILES_PB), QProfiles.parser());
    assertThat(qProfiles.getQprofilesByKeyMap()).containsOnlyKeys(
      "cs-sonar-way-58886",
      "java-sonar-way-74592",
      "java-empty-74333",
      "js-sonar-security-way-70539",
      "js-sonar-way-60746");

    assertThat(qProfiles.getDefaultQProfilesByLanguageMap()).containsOnly(
      entry("cs", "cs-sonar-way-58886"),
      entry("java", "java-sonar-way-74592"),
      entry("js", "js-sonar-way-60746"));
  }

  @Test
  public void testParsingError() throws IOException {
    // wrong file
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithStreamResponse("/api/qualityprofiles/search.protobuf", "/update/all_projects.json");
    qProfilesDownloader = new QualityProfilesDownloader(wsClient);

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Failed to load default quality profiles");
    qProfilesDownloader.fetchQualityProfilesTo(temp.getRoot().toPath());

  }

}

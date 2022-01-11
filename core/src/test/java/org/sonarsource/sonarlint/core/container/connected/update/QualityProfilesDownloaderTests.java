/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.QualityProfileStore;
import org.sonarsource.sonarlint.core.container.storage.StorageFolder;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QualityProfilesDownloaderTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private QualityProfilesDownloader underTest;

  @Test
  void test(@TempDir Path tempDir) {
    mockServer.addResponseFromResource("/api/qualityprofiles/search.protobuf", "/update/qualityprofiles.pb");
    QualityProfileStore qualityProfileStore = new QualityProfileStore(new StorageFolder.Default(tempDir));
    underTest = new QualityProfilesDownloader(mockServer.serverApiHelper(), qualityProfileStore);

    underTest.fetchQualityProfiles();

    QProfiles qProfiles = ProtobufUtil.readFile(tempDir.resolve(QualityProfileStore.QUALITY_PROFILES_PB), QProfiles.parser());
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
  void testWithOrg(@TempDir Path tempDir) {
    mockServer.addResponseFromResource("/api/qualityprofiles/search.protobuf?organization=myOrg", "/update/qualityprofiles.pb");
    QualityProfileStore qualityProfileStore = new QualityProfileStore(new StorageFolder.Default(tempDir));
    underTest = new QualityProfilesDownloader(mockServer.serverApiHelper("myOrg"), qualityProfileStore);

    underTest.fetchQualityProfiles();

    QProfiles qProfiles = ProtobufUtil.readFile(tempDir.resolve(QualityProfileStore.QUALITY_PROFILES_PB), QProfiles.parser());
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
  void testParsingError(@TempDir Path tempDir) throws IOException {
    // wrong file
    mockServer.addStringResponse("/api/qualityprofiles/search.protobuf", "foo bar");
    QualityProfileStore qualityProfileStore = new QualityProfileStore(new StorageFolder.Default(tempDir));

    underTest = new QualityProfilesDownloader(mockServer.serverApiHelper(), qualityProfileStore);

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.fetchQualityProfiles());
    assertThat(thrown).hasMessageContaining("Protocol message tag had invalid wire type");

  }

}

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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.Qualityprofiles;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.QualityProfileStore;
import org.sonarsource.sonarlint.core.container.storage.StorageFolder;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QualityProfilesDownloaderTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();
  private final QualityProfileStore currentQualityProfileStore = mock(QualityProfileStore.class);

  private QualityProfilesDownloader underTest;

  @BeforeEach
  void setUp() {
    when(currentQualityProfileStore.getAllOrEmpty()).thenReturn(QProfiles.newBuilder().build());
  }

  @Test
  void test(@TempDir Path tempDir) {
    mockServer.addResponseFromResource("/api/qualityprofiles/search.protobuf", "/update/qualityprofiles.pb");
    QualityProfileStore qualityProfileStore = new QualityProfileStore(new StorageFolder.Default(tempDir));
    underTest = new QualityProfilesDownloader(mockServer.serverApiHelper(), qualityProfileStore);

    underTest.fetchQualityProfiles(currentQualityProfileStore);

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

    underTest.fetchQualityProfiles(currentQualityProfileStore);

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
  void testParsingError(@TempDir Path tempDir) {
    // wrong file
    mockServer.addStringResponse("/api/qualityprofiles/search.protobuf", "foo bar");
    QualityProfileStore qualityProfileStore = new QualityProfileStore(new StorageFolder.Default(tempDir));

    underTest = new QualityProfilesDownloader(mockServer.serverApiHelper(), qualityProfileStore);

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.fetchQualityProfiles(currentQualityProfileStore));
    assertThat(thrown).hasMessageContaining("Protocol message tag had invalid wire type");

  }

  @Test
  void testUpdateEvents(@TempDir Path tempDir) {
    mockServer.addProtobufResponse("/api/qualityprofiles/search.protobuf", Qualityprofiles.SearchWsResponse.newBuilder()
      .addProfiles(Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder().setKey("key1").setLanguage("languageKey1").setActiveRuleCount(9).build())
      .addProfiles(Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder().setKey("key2").setLanguage("languageKey2").setActiveRuleCount(5).build())
      .build());

    when(currentQualityProfileStore.getAllOrEmpty()).thenReturn(QProfiles.newBuilder()
      .putQprofilesByKey("key1", QProfiles.QProfile.newBuilder().setLanguage("languageKey1").setActiveRuleCount(10).build())
      .putQprofilesByKey("key3", QProfiles.QProfile.newBuilder().setLanguage("languageKey3").setActiveRuleCount(15).build())
      .build());
    QualityProfileStore qualityProfileStore = new QualityProfileStore(new StorageFolder.Default(tempDir));

    underTest = new QualityProfilesDownloader(mockServer.serverApiHelper(), qualityProfileStore);

    List<UpdateEvent> events = underTest.fetchQualityProfiles(currentQualityProfileStore);

    assertThat(events)
      .hasSize(3)
      .hasOnlyElementsOfTypes(
        QualityProfilesDownloader.QualityProfileAdded.class,
        QualityProfilesDownloader.QualityProfileRemoved.class,
        QualityProfilesDownloader.QualityProfileChanged.class);

  }

}

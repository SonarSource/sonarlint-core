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
package org.sonarsource.sonarlint.core.container.connected.update.check;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.Qualityprofiles;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.container.storage.QualityProfileStore;
import org.sonarsource.sonarlint.core.container.storage.StorageFolder;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles.QProfile;

import static org.assertj.core.api.Assertions.assertThat;

class QualityProfilesUpdateCheckerTest {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  @TempDir
  Path tempDir;

  private QualityProfilesUpdateChecker checker;
  private QualityProfileStore qualityProfileStore;

  @BeforeEach
  public void prepare() {
    qualityProfileStore = new QualityProfileStore(new StorageFolder.Default(tempDir));
    qualityProfileStore.store(QProfiles.newBuilder().build());
    mockServer.addProtobufResponse("/api/qualityprofiles/search.protobuf", Qualityprofiles.SearchWsResponse.newBuilder().build());

    checker = new QualityProfilesUpdateChecker(mockServer.serverApiHelper());
  }

  @Test
  void testNoChanges() {
    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(qualityProfileStore, result);

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
  }

  @Test
  void addedQProfile() {
    mockServer.addProtobufResponse("/api/qualityprofiles/search.protobuf",
      Qualityprofiles.SearchWsResponse.newBuilder().addProfiles(
        Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder().setKey("java-123").setLanguageName("Java")
          .setName("Sonar Way").build())
        .build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(qualityProfileStore, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profile 'Sonar Way' for language 'Java' added");
  }

  @Test
  void removedQProfile() {
    qualityProfileStore.store(QProfiles.newBuilder().putQprofilesByKey("java-123", QProfile.newBuilder().setKey("java-123").setName("Sonar Way")
        .setLanguageName("Java").build()).build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(qualityProfileStore, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profile 'Sonar Way' for language 'Java' removed");
  }

  @Test
  void updatedQProfile_rules_updated_at() {
    mockServer.addProtobufResponse("/api/qualityprofiles/search.protobuf",
      Qualityprofiles.SearchWsResponse.newBuilder().addProfiles(
        Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder().setKey("java-123").setLanguageName("Java")
          .setName("Sonar Way").setRulesUpdatedAt("foo").build())
        .build());
    qualityProfileStore.store(QProfiles.newBuilder().putQprofilesByKey("java-123",
        QProfile.newBuilder()
          .setKey("java-123")
          .setName("Sonar Way")
          .setLanguageName("Java")
          .setRulesUpdatedAt("foo2")
          .build())
        .build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(qualityProfileStore, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profile 'Sonar Way' for language 'Java' updated");
  }

  @Test
  void updatedQProfile_user_updated_at() {
    mockServer.addProtobufResponse("/api/qualityprofiles/search.protobuf",
      Qualityprofiles.SearchWsResponse.newBuilder().addProfiles(
        Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder().setKey("java-123").setLanguageName("Java")
          .setName("Sonar Way").setRulesUpdatedAt("foo").setUserUpdatedAt("user").build())
        .build());
    qualityProfileStore.store(QProfiles.newBuilder().putQprofilesByKey("java-123",
        QProfile.newBuilder()
          .setKey("java-123")
          .setName("Sonar Way")
          .setLanguageName("Java")
          .setRulesUpdatedAt("foo")
          .build())
        .build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(qualityProfileStore, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profile 'Sonar Way' for language 'Java' updated");
  }
}

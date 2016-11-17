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
package org.sonarsource.sonarlint.core.container.connected.update.check;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.container.connected.update.QualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles.QProfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QualityProfilesUpdateCheckerTest {

  private QualityProfilesUpdateChecker checker;
  private StorageManager storageManager;
  private QualityProfilesDownloader qualityProfilesDownloader;

  @Before
  public void prepare() {

    storageManager = mock(StorageManager.class);
    qualityProfilesDownloader = mock(QualityProfilesDownloader.class);

    when(storageManager.readQProfilesFromStorage()).thenReturn(QProfiles.newBuilder().build());
    when(qualityProfilesDownloader.fetchQualityProfiles()).thenReturn(QProfiles.newBuilder().build());

    checker = new QualityProfilesUpdateChecker(storageManager, qualityProfilesDownloader);
  }

  @Test
  public void testNoChanges() {
    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result);

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
  }

  @Test
  public void addedQProfile() {
    when(qualityProfilesDownloader.fetchQualityProfiles())
      .thenReturn(QProfiles.newBuilder().putQprofilesByKey("java-123", QProfile.newBuilder().setKey("java-123").build()).build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profile 'java-123' added");
  }

  @Test
  public void removedQProfile() {
    when(storageManager.readQProfilesFromStorage())
      .thenReturn(QProfiles.newBuilder().putQprofilesByKey("java-123", QProfile.newBuilder().setKey("java-123").build()).build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profile 'java-123' removed");
  }

  @Test
  public void updatedQProfile() {
    when(qualityProfilesDownloader.fetchQualityProfiles())
      .thenReturn(QProfiles.newBuilder().putQprofilesByKey("java-123", QProfile.newBuilder().setKey("java-123").setRulesUpdatedAt("foo").build()).build());
    when(storageManager.readQProfilesFromStorage())
      .thenReturn(QProfiles.newBuilder().putQprofilesByKey("java-123", QProfile.newBuilder().setKey("java-123").setRulesUpdatedAt("foo2").build()).build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profile 'java-123' updated");
  }

}

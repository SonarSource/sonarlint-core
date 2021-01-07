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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.container.connected.update.PluginListDownloader;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class GlobalStorageUpdateCheckerTest {

  private GlobalStorageUpdateChecker checker;
  private ServerVersionAndStatusChecker statusChecker;

  @Before
  public void prepare() {
    statusChecker = mock(ServerVersionAndStatusChecker.class);
    when(statusChecker.checkVersionAndStatus()).thenReturn(ServerInfos.newBuilder().build());
    checker = new GlobalStorageUpdateChecker(statusChecker, mock(PluginsUpdateChecker.class),
      mock(PluginListDownloader.class), mock(GlobalSettingsUpdateChecker.class), mock(QualityProfilesUpdateChecker.class));
  }

  @Test
  public void testNoChanges() {
    StorageUpdateCheckResult result = checker.checkForUpdate(mock(ProgressWrapper.class));

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
  }
}

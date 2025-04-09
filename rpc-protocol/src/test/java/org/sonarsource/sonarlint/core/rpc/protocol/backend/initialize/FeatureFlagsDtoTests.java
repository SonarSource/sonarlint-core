/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class FeatureFlagsDtoTests {

  @Test
  void should_work_with_deprecated_constructor() {
    var featureFlagsDto = new FeatureFlagsDto(true, false, true, false, true,
      false, true, false, true, false, true);
    
    assertThat(featureFlagsDto.shouldManageSmartNotifications()).isTrue();
    assertThat(featureFlagsDto.areTaintVulnerabilitiesEnabled()).isFalse();
    assertThat(featureFlagsDto.shouldSynchronizeProjects()).isTrue();
    assertThat(featureFlagsDto.shouldManageLocalServer()).isFalse();
    assertThat(featureFlagsDto.isEnableSecurityHotspots()).isTrue();
    assertThat(featureFlagsDto.shouldManageServerSentEvents()).isFalse();
    assertThat(featureFlagsDto.isEnableDataflowBugDetection()).isTrue();
    assertThat(featureFlagsDto.shouldManageFullSynchronization()).isFalse();
    assertThat(featureFlagsDto.isEnableTelemetry()).isTrue();
    assertThat(featureFlagsDto.canOpenFixSuggestion()).isFalse();
    assertThat(featureFlagsDto.isEnableMonitoring()).isTrue();
  }
}

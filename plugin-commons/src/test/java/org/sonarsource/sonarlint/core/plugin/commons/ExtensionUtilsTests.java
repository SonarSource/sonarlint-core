/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons;

import org.junit.jupiter.api.Test;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.ScannerSide;
import org.sonar.api.ce.ComputeEngineSide;
import org.sonar.api.server.ServerSide;
import org.sonarsource.api.sonarlint.SonarLintSide;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionUtilsTests {

  @Test
  void shouldBeBatchInstantiationStrategy() {
    assertThat(ExtensionUtils.isInstantiationStrategy(DefaultScannerService.class, InstantiationStrategy.PER_BATCH)).isFalse();
    assertThat(ExtensionUtils.isInstantiationStrategy(new DefaultScannerService(), InstantiationStrategy.PER_BATCH)).isFalse();

  }

  @Test
  void shouldBeProjectInstantiationStrategy() {
    assertThat(ExtensionUtils.isInstantiationStrategy(DefaultScannerService.class, InstantiationStrategy.PER_PROJECT)).isTrue();
    assertThat(ExtensionUtils.isInstantiationStrategy(new DefaultScannerService(), InstantiationStrategy.PER_PROJECT)).isTrue();

  }

  @Test
  void testIsSonarLintSide() {
    assertThat(ExtensionUtils.isSonarLintSide(ScannerService.class)).isFalse();

    assertThat(ExtensionUtils.isSonarLintSide(ServerService.class)).isFalse();
    assertThat(ExtensionUtils.isSonarLintSide(new ServerService())).isFalse();
    assertThat(ExtensionUtils.isSonarLintSide(new WebServerService())).isFalse();
    assertThat(ExtensionUtils.isSonarLintSide(new ComputeEngineService())).isFalse();
    assertThat(ExtensionUtils.isSonarLintSide(new DefaultSonarLintService())).isTrue();
  }

  @ScannerSide
  @InstantiationStrategy(InstantiationStrategy.PER_BATCH)
  public static class ScannerService {

  }

  @ScannerSide
  public static class DefaultScannerService {

  }

  @SonarLintSide
  public static class DefaultSonarLintService {

  }

  @ServerSide
  public static class ServerService {

  }

  @ServerSide
  public static class WebServerService {

  }

  @ComputeEngineSide
  public static class ComputeEngineService {

  }

}

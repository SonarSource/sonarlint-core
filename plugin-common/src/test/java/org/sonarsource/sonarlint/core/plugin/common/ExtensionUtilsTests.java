/*
 * SonarLint Core - Plugin Common
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
package org.sonarsource.sonarlint.core.plugin.common;

import org.junit.jupiter.api.Test;
import org.sonar.api.BatchComponent;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.ScannerSide;
import org.sonar.api.ce.ComputeEngineSide;
import org.sonar.api.server.ServerSide;
import org.sonarsource.api.sonarlint.SonarLintSide;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionUtilsTests {

  @Test
  void testIsSonarLintSide() {
    assertThat(ExtensionUtils.isSonarLintSide(ScannerService.class)).isFalse();
    assertThat(ExtensionUtils.isSonarLintSide(DeprecatedBatchService.class)).isFalse();

    assertThat(ExtensionUtils.isSonarLintSide(ServerService.class)).isFalse();
    assertThat(ExtensionUtils.isSonarLintSide(new ServerService())).isFalse();
    assertThat(ExtensionUtils.isSonarLintSide(new WebServerService())).isFalse();
    assertThat(ExtensionUtils.isSonarLintSide(new ComputeEngineService())).isFalse();
    assertThat(ExtensionUtils.isSonarLintSide(new DefaultSonarLintService())).isTrue();
  }

  @Test
  void testIsType() {
    assertThat(ExtensionUtils.isType(new DeprecatedBatchService(), BatchComponent.class)).isTrue();
    assertThat(ExtensionUtils.isType(DeprecatedBatchService.class, BatchComponent.class)).isTrue();
    assertThat(ExtensionUtils.isType(DeprecatedBatchService.class, DeprecatedBatchService.class)).isTrue();
    assertThat(ExtensionUtils.isType(DeprecatedBatchService.class, String.class)).isFalse();
  }

  @ScannerSide
  @InstantiationStrategy(InstantiationStrategy.PER_BATCH)
  public static class ScannerService {

  }

  public static class DeprecatedBatchService implements BatchComponent {

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

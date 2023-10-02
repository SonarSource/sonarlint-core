/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.sonarsource.sonarlint.core.serverconnection.storage.NewCodeDefinitionStorage.adapt;

class NewCodeDefinitionStorageTests {

  @Test
  void shouldAdaptToProtobuf() {
    var days = adapt(NewCodeDefinition.withNumberOfDays(30, 1000));
    assertThat(days.getDays()).isEqualTo(30);
    assertThat(days.getThresholdDate()).isEqualTo(1000);

    var previousWithVersion = adapt(NewCodeDefinition.withPreviousVersion(1000, "1.0-SNAPSHOT"));
    assertThat(previousWithVersion.getVersion()).isEqualTo("1.0-SNAPSHOT");
    assertThat(previousWithVersion.getThresholdDate()).isEqualTo(1000);

    var previousWithoutVersion = adapt(NewCodeDefinition.withPreviousVersion(1000, null));
    assertThat(previousWithoutVersion.getVersion()).isEmpty();
    assertThat(previousWithoutVersion.getThresholdDate()).isEqualTo(1000);

    var branch = adapt(NewCodeDefinition.withReferenceBranch("master"));
    assertThat(branch.getReferenceBranch()).isEqualTo("master");
  }

  @Test
  void shouldAdaptFromProtobuf() {
    var daysProto = Sonarlint.NewCodeDefinition.newBuilder()
      .setMode(Sonarlint.NewCodeDefinitionMode.NUMBER_OF_DAYS)
      .setDays(30)
      .setThresholdDate(1000)
      .build();
    var days = adapt(daysProto);
    assertThat(days.isSupported()).isTrue();
    assertThat(days).isInstanceOf(NewCodeDefinition.NewCodeNumberOfDays.class);
    assertThat(((NewCodeDefinition.NewCodeNumberOfDays) days).getThresholdDate()).isEqualTo(1000);
    assertThat(((NewCodeDefinition.NewCodeNumberOfDays) days).getDays()).isEqualTo(30);

    var previousWithVersionProto = Sonarlint.NewCodeDefinition.newBuilder()
      .setMode(Sonarlint.NewCodeDefinitionMode.PREVIOUS_VERSION)
      .setVersion("1.0-SNAPSHOT")
      .setThresholdDate(1000)
      .build();
    var previousWithVersion = adapt(previousWithVersionProto);
    assertThat(previousWithVersion.isSupported()).isTrue();
    assertThat(previousWithVersion).isInstanceOf(NewCodeDefinition.NewCodePreviousVersion.class);
    assertThat(((NewCodeDefinition.NewCodePreviousVersion) previousWithVersion).getThresholdDate()).isEqualTo(1000);
    assertThat(((NewCodeDefinition.NewCodePreviousVersion) previousWithVersion).getVersion()).isEqualTo("1.0-SNAPSHOT");

    var previousWithoutVersionProto = Sonarlint.NewCodeDefinition.newBuilder()
      .setMode(Sonarlint.NewCodeDefinitionMode.PREVIOUS_VERSION)
      .setThresholdDate(1000)
      .build();
    var previousWithoutVersion = adapt(previousWithoutVersionProto);
    assertThat(previousWithoutVersion.isSupported()).isTrue();
    assertThat(previousWithoutVersion).isInstanceOf(NewCodeDefinition.NewCodePreviousVersion.class);
    assertThat(((NewCodeDefinition.NewCodePreviousVersion) previousWithoutVersion).getThresholdDate()).isEqualTo(1000);
    assertThat(((NewCodeDefinition.NewCodePreviousVersion) previousWithoutVersion).getVersion()).isNull();

    var branchProto = Sonarlint.NewCodeDefinition.newBuilder()
      .setMode(Sonarlint.NewCodeDefinitionMode.REFERENCE_BRANCH)
      .setReferenceBranch("master")
      .build();
    var branch = adapt(branchProto);
    assertThat(branch.isSupported()).isFalse();
    assertThat(branch).isInstanceOf(NewCodeDefinition.NewCodeReferenceBranch.class);
    assertThat(((NewCodeDefinition.NewCodeReferenceBranch) branch).getBranchName()).isEqualTo("master");

    var analysisProto = Sonarlint.NewCodeDefinition.newBuilder()
      .setMode(Sonarlint.NewCodeDefinitionMode.SPECIFIC_ANALYSIS)
      .setThresholdDate(1000)
      .build();
    var analysis = adapt(analysisProto);
    assertThat(analysis.isSupported()).isTrue();
    assertThat(analysis).isInstanceOf(NewCodeDefinition.NewCodeSpecificAnalysis.class);
    assertThat(((NewCodeDefinition.NewCodeSpecificAnalysis) analysis).getThresholdDate()).isEqualTo(1000);

    var unknownProto = Sonarlint.NewCodeDefinition.newBuilder()
      .setMode(Sonarlint.NewCodeDefinitionMode.UNKNOWN)
      .build();
    assertThatThrownBy(() -> adapt(unknownProto)).hasMessage("Unsupported mode: UNKNOWN");
  }

}

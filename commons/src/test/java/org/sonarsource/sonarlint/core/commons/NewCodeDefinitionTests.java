/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewCodeDefinitionTests {
  @Test
  void isOnNewCodeTest() {
    var analysisDate = Instant.parse("2023-09-12T10:15:30.00Z");
    var issueCreationDateBeforeAnalysis = Instant.parse("2023-09-10T10:15:30.00Z");
    var issueCreationDateAfterAnalysis = Instant.parse("2023-09-14T10:15:30.00Z");
    var newCodeDefinitionWithoutDate = NewCodeDefinition.withNumberOfDays(30, 0);
    var newCodeDefinitionWithDate = NewCodeDefinition.withNumberOfDays(30, analysisDate.toEpochMilli());

    assertThat(newCodeDefinitionWithoutDate.isOnNewCode(analysisDate.toEpochMilli())).isTrue();
    assertThat(newCodeDefinitionWithDate.isOnNewCode(issueCreationDateAfterAnalysis.toEpochMilli())).isTrue();
    assertThat(newCodeDefinitionWithDate.isOnNewCode(issueCreationDateBeforeAnalysis.toEpochMilli())).isFalse();
  }

  @Test
  void toStringTest() {
    var analysisDate = Instant.parse("2023-09-12T10:15:30.00Z").toEpochMilli();
    var numberOfDays = NewCodeDefinition.withNumberOfDays(30, analysisDate);
    var previousVersion = NewCodeDefinition.withPreviousVersion(analysisDate, "version");
    var specificAnalysis = NewCodeDefinition.withSpecificAnalysis(analysisDate);
    var referenceBranch = NewCodeDefinition.withReferenceBranch("referenceBranch");

    assertThat(numberOfDays).hasToString("From last 30 days");
    assertThat(previousVersion).hasToString("Since version version");
    assertThat(specificAnalysis).hasToString("Since analysis from 2023-09-12T10:15:30Z");
    assertThat(referenceBranch).hasToString("Compared to branch referenceBranch (not supported)");
  }

}

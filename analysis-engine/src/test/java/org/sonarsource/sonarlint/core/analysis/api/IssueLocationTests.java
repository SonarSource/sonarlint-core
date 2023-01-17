/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.api;

import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.TextRange;

import static org.assertj.core.api.Assertions.assertThat;

class IssueLocationTests {

  @Test
  void it_should_return_text_range_details_if_provided() {
    var issueLocation = newIssueLocation(new TextRange(1, 2, 3, 4));

    assertThat(issueLocation.getStartLine()).isEqualTo(1);
    assertThat(issueLocation.getStartLineOffset()).isEqualTo(2);
    assertThat(issueLocation.getEndLine()).isEqualTo(3);
    assertThat(issueLocation.getEndLineOffset()).isEqualTo(4);
  }

  @Test
  void it_should_return_null_details_if_no_text_range_provided() {
    var issueLocation = newIssueLocation(null);

    assertThat(issueLocation.getStartLine()).isNull();
    assertThat(issueLocation.getStartLineOffset()).isNull();
    assertThat(issueLocation.getEndLine()).isNull();
    assertThat(issueLocation.getEndLineOffset()).isNull();
  }

  private static IssueLocation newIssueLocation(@Nullable TextRange textRange) {
    return new IssueLocation() {
      @Override
      public ClientInputFile getInputFile() {
        return null;
      }

      @Override
      public TextRange getTextRange() {
        return textRange;
      }

      @Override
      public String getMessage() {
        return null;
      }
    };
  }
}

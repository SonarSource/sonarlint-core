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
package org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.sonarsource.sonarlint.core.container.analysis.ServerConfigurationProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class IssueInclusionPatternInitializerTest {

  @Test
  public void testNoConfiguration() {
    IssueInclusionPatternInitializer patternsInitializer = new IssueInclusionPatternInitializer(new ServerConfigurationProvider(Collections.emptyMap()));
    patternsInitializer.initPatterns();
    assertThat(patternsInitializer.hasConfiguredPatterns()).isFalse();
  }

  @Test
  public void shouldHavePatternsBasedOnMulticriteriaPattern() {
    Map<String, String> settings = new HashMap<>();
    settings.put("sonar.issue.enforce" + ".multicriteria", "1,2");
    settings.put("sonar.issue.enforce" + ".multicriteria" + ".1." + "resourceKey", "org/foo/Bar.java");
    settings.put("sonar.issue.enforce" + ".multicriteria" + ".1." + "ruleKey", "*");
    settings.put("sonar.issue.enforce" + ".multicriteria" + ".2." + "resourceKey", "org/foo/Hello.java");
    settings.put("sonar.issue.enforce" + ".multicriteria" + ".2." + "ruleKey", "checkstyle:MagicNumber");
    IssueInclusionPatternInitializer patternsInitializer = new IssueInclusionPatternInitializer(new ServerConfigurationProvider(settings));
    patternsInitializer.initPatterns();

    assertThat(patternsInitializer.hasConfiguredPatterns()).isTrue();
    assertThat(patternsInitializer.hasMulticriteriaPatterns()).isTrue();
    assertThat(patternsInitializer.getMulticriteriaPatterns().size()).isEqualTo(2);
  }

}

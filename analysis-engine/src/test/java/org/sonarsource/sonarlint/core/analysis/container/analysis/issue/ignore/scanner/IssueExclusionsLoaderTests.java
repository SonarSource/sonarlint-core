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
package org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.scanner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.FileMetadata.Metadata;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.IgnoreIssuesFilter;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern.IssueExclusionPatternInitializer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern.IssuePattern;
import testutils.OnDiskTestClientInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class IssueExclusionsLoaderTests {

  private IssueExclusionPatternInitializer exclusionPatternInitializer;
  private IgnoreIssuesFilter ignoreIssuesFilter;

  private IssueExclusionsLoader scanner;

  @BeforeEach
  void before() throws Exception {
    exclusionPatternInitializer = mock(IssueExclusionPatternInitializer.class);
    ignoreIssuesFilter = mock(IgnoreIssuesFilter.class);
    scanner = new IssueExclusionsLoader(exclusionPatternInitializer, ignoreIssuesFilter);
  }

  private SonarLintInputFile createFile(String path) {
    return new SonarLintInputFile(new OnDiskTestClientInputFile(Paths.get(path), path, false, StandardCharsets.UTF_8), f -> mock(Metadata.class));
  }

  @Test
  void testToString() {
    assertThat(scanner).hasToString("Issues Exclusions - Source Scanner");
  }

  @Test
  void createComputer() {

    assertThat(scanner.createCharHandlerFor(createFile("src/main/java/Foo.java"))).isNull();

    when(exclusionPatternInitializer.getAllFilePatterns()).thenReturn(Collections.singletonList("pattern"));
    scanner = new IssueExclusionsLoader(exclusionPatternInitializer, ignoreIssuesFilter);
    assertThat(scanner.createCharHandlerFor(createFile("src/main/java/Foo.java"))).isNotNull();

  }

  @Test
  void populateRuleExclusionPatterns() {
    var pattern1 = new IssuePattern("org/foo/Bar*.java", "*");
    var pattern2 = new IssuePattern("org/foo/Hell?.java", "checkstyle:MagicNumber");
    when(exclusionPatternInitializer.getMulticriteriaPatterns()).thenReturn(Arrays.asList(pattern1, pattern2));

    var loader = new IssueExclusionsLoader(exclusionPatternInitializer, ignoreIssuesFilter);
    var file1 = createFile("org/foo/Bar.java");
    loader.addMulticriteriaPatterns(file1);
    var file2 = createFile("org/foo/Baz.java");
    loader.addMulticriteriaPatterns(file2);
    var file3 = createFile("org/foo/Hello.java");
    loader.addMulticriteriaPatterns(file3);

    verify(ignoreIssuesFilter).addRuleExclusionPatternForComponent(file1, pattern1.getRulePattern());
    verify(ignoreIssuesFilter).addRuleExclusionPatternForComponent(file3, pattern2.getRulePattern());
    verifyNoMoreInteractions(ignoreIssuesFilter);
  }

}

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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.FileMetadata;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.FileMetadata.Metadata;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern.IssueExclusionPatternInitializer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.scanner.IssueExclusionsLoader.DoubleRegexpMatcher;
import testutils.OnDiskTestClientInputFile;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

class IssueExclusionsRegexpScannerTests {
  private SonarLintInputFile javaFile;

  @Mock
  private IssueExclusionPatternInitializer patternsInitializer;

  private List<Pattern> allFilePatterns;
  private List<DoubleRegexpMatcher> blockPatterns;
  private IssueExclusionsRegexpScanner regexpScanner;
  private final FileMetadata fileMetadata = new FileMetadata();

  @BeforeEach
  void init() {
    MockitoAnnotations.initMocks(this);

    blockPatterns = Arrays.asList(new DoubleRegexpMatcher(Pattern.compile("// SONAR-OFF"), Pattern.compile("// SONAR-ON")),
      new DoubleRegexpMatcher(Pattern.compile("// FOO-OFF"), Pattern.compile("// FOO-ON")));
    allFilePatterns = Collections.singletonList(Pattern.compile("@SONAR-IGNORE-ALL"));

    javaFile = new SonarLintInputFile(new OnDiskTestClientInputFile(Paths.get("src/Foo.java"), "src/Foo.java", false, StandardCharsets.UTF_8), f -> mock(Metadata.class));
    regexpScanner = new IssueExclusionsRegexpScanner(javaFile, allFilePatterns, blockPatterns);
  }

  @Test
  void shouldDetectPatternLastLine() throws URISyntaxException, IOException {
    var filePath = getResource("file-with-single-regexp-last-line.txt");
    fileMetadata.readMetadata(Files.newInputStream(filePath), UTF_8, filePath.toUri(), regexpScanner);

    assertThat(javaFile.isIgnoreAllIssues()).isTrue();
  }

  @Test
  void shouldDoNothing() throws Exception {
    var filePath = getResource("file-with-no-regexp.txt");
    fileMetadata.readMetadata(Files.newInputStream(filePath), UTF_8, filePath.toUri(), regexpScanner);

    assertThat(javaFile.isIgnoreAllIssues()).isFalse();
  }

  @Test
  void shouldExcludeAllIssues() throws Exception {
    var filePath = getResource("file-with-single-regexp.txt");
    fileMetadata.readMetadata(Files.newInputStream(filePath), UTF_8, filePath.toUri(), regexpScanner);

    assertThat(javaFile.isIgnoreAllIssues()).isTrue();
  }

  @Test
  void shouldExcludeAllIssuesEvenIfAlsoDoubleRegexps() throws Exception {
    var filePath = getResource("file-with-single-regexp-and-double-regexp.txt");
    fileMetadata.readMetadata(Files.newInputStream(filePath), UTF_8, filePath.toUri(), regexpScanner);

    assertThat(javaFile.isIgnoreAllIssues()).isTrue();
  }

  @Test
  void shouldExcludeLines() throws Exception {
    var filePath = getResource("file-with-double-regexp.txt");
    fileMetadata.readMetadata(Files.newInputStream(filePath), UTF_8, filePath.toUri(), regexpScanner);

    assertThat(javaFile.isIgnoreAllIssues()).isFalse();
    assertThat(IntStream.rangeClosed(1, 20).noneMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
    assertThat(IntStream.rangeClosed(21, 25).allMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
    assertThat(IntStream.rangeClosed(26, 34).noneMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
  }

  @Test
  void shouldAddPatternToExcludeLinesTillTheEnd() throws Exception {
    var filePath = getResource("file-with-double-regexp-unfinished.txt");
    fileMetadata.readMetadata(Files.newInputStream(filePath), UTF_8, filePath.toUri(), regexpScanner);

    assertThat(javaFile.isIgnoreAllIssues()).isFalse();
    assertThat(IntStream.rangeClosed(1, 20).noneMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
    assertThat(IntStream.rangeClosed(21, 34).allMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
  }

  @Test
  void shouldAddPatternToExcludeSeveralLineRanges() throws Exception {
    var filePath = getResource("file-with-double-regexp-twice.txt");
    fileMetadata.readMetadata(Files.newInputStream(filePath), UTF_8, filePath.toUri(), regexpScanner);

    assertThat(javaFile.isIgnoreAllIssues()).isFalse();
    assertThat(IntStream.rangeClosed(1, 20).noneMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
    assertThat(IntStream.rangeClosed(21, 25).allMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
    assertThat(IntStream.rangeClosed(26, 28).noneMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
    assertThat(IntStream.rangeClosed(29, 33).allMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
  }

  @Test
  void shouldAddPatternToExcludeLinesWithWrongOrder() throws Exception {
    var filePath = getResource("file-with-double-regexp-wrong-order.txt");
    fileMetadata.readMetadata(Files.newInputStream(filePath), UTF_8, filePath.toUri(), regexpScanner);

    assertThat(IntStream.rangeClosed(1, 24).noneMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
    assertThat(IntStream.rangeClosed(25, 35).allMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
  }

  @Test
  void shouldAddPatternToExcludeLinesWithMess() throws Exception {
    var filePath = getResource("file-with-double-regexp-mess.txt");
    fileMetadata.readMetadata(Files.newInputStream(filePath), UTF_8, filePath.toUri(), regexpScanner);

    assertThat(IntStream.rangeClosed(1, 20).noneMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
    assertThat(IntStream.rangeClosed(21, 29).allMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
    assertThat(IntStream.rangeClosed(30, 37).noneMatch(javaFile::isIgnoreAllIssuesOnLine)).isTrue();
  }

  private Path getResource(String fileName) throws URISyntaxException {
    return Paths.get(this.getClass().getResource("/IssueExclusionsRegexpScannerTests/" + fileName).toURI());
  }

}

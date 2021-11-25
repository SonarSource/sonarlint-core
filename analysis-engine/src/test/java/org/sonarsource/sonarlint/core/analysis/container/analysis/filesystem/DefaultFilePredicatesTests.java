/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonarsource.sonarlint.core.plugin.common.Language;
import testutils.OnDiskTestClientInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class DefaultFilePredicatesTests {

  private InputFile javaFile;
  private FilePredicates predicates;

  @TempDir
  Path baseDir;

  @BeforeEach
  public void before() throws IOException {
    predicates = new DefaultFilePredicates();
    Path filePath = baseDir.resolve("src/main/java/struts/Action.java");
    Files.createDirectories(filePath.getParent());
    Files.write(filePath, "foo".getBytes(StandardCharsets.UTF_8));
    OnDiskTestClientInputFile clientInputFile = new OnDiskTestClientInputFile(filePath, "src/main/java/struts/Action.java", false, StandardCharsets.UTF_8, Language.JAVA);
    javaFile = new SonarLintInputFile(clientInputFile, f -> new FileMetadata().readMetadata(filePath.toFile(), StandardCharsets.UTF_8))
      .setType(Type.MAIN)
      .setLanguage(Language.JAVA);
  }

  @Test
  public void all() {
    assertThat(predicates.all().apply(javaFile)).isTrue();
  }

  @Test
  public void none() {
    assertThat(predicates.none().apply(javaFile)).isFalse();
  }

  @Test
  public void matches_inclusion_pattern() {
    assertThat(predicates.matchesPathPattern("file:**/src/main/**/Action.java").apply(javaFile)).isTrue();
    assertThat(predicates.matchesPathPattern("**/src/main/**/Action.java").apply(javaFile)).isTrue();
    assertThat(predicates.matchesPathPattern("src/main/**/Action.java").apply(javaFile)).isTrue();
    assertThat(predicates.matchesPathPattern("src/**/*.php").apply(javaFile)).isFalse();
  }

  @Test
  public void matches_inclusion_patterns() {
    assertThat(predicates.matchesPathPatterns(new String[] {"src/other/**.java", "src/main/**/Action.java"}).apply(javaFile)).isTrue();
    assertThat(predicates.matchesPathPatterns(new String[] {}).apply(javaFile)).isTrue();
    assertThat(predicates.matchesPathPatterns(new String[] {"src/other/**.java", "src/**/*.php"}).apply(javaFile)).isFalse();
  }

  @Test
  public void does_not_match_exclusion_pattern() {
    assertThat(predicates.doesNotMatchPathPattern("src/main/**/Action.java").apply(javaFile)).isFalse();
    assertThat(predicates.doesNotMatchPathPattern("src/**/*.php").apply(javaFile)).isTrue();
  }

  @Test
  public void does_not_match_exclusion_patterns() {
    assertThat(predicates.doesNotMatchPathPatterns(new String[] {}).apply(javaFile)).isTrue();
    assertThat(predicates.doesNotMatchPathPatterns(new String[] {"src/other/**.java", "src/**/*.php"}).apply(javaFile)).isTrue();
    assertThat(predicates.doesNotMatchPathPatterns(new String[] {"src/other/**.java", "src/main/**/Action.java"}).apply(javaFile)).isFalse();
  }

  @Test
  public void has_relative_path_unsupported() {
    assertThrows(UnsupportedOperationException.class, () -> predicates.hasRelativePath("src/main/java/struts/Action.java").apply(javaFile));
  }

  @Test
  public void has_uri() throws Exception {
    URI uri = javaFile.uri();
    assertThat(predicates.hasURI(uri).apply(javaFile)).isTrue();

    assertThat(predicates.hasURI(baseDir.resolve("another.php").toUri()).apply(javaFile)).isFalse();
  }

  @Test
  public void has_name() throws IOException {
    String fileName = javaFile.filename();
    assertThat(predicates.hasFilename(fileName).apply(javaFile)).isTrue();

    assertThat(predicates.hasFilename("another.php").apply(javaFile)).isFalse();
    assertThat(predicates.hasFilename("Action.php").apply(javaFile)).isFalse();
  }

  @Test
  public void has_extension() throws IOException {
    String extension = "java";
    assertThat(predicates.hasExtension(extension).apply(javaFile)).isTrue();

    assertThat(predicates.hasExtension("php").apply(javaFile)).isFalse();
    assertThat(predicates.hasExtension("").apply(javaFile)).isFalse();

  }

  @Test
  public void has_path() throws Exception {
    assertThrows(UnsupportedOperationException.class, () -> predicates.hasPath("src/main/java/struts/Action.java").apply(javaFile));
  }

  @Test
  public void is_file() throws Exception {
    assertThat(predicates.is(javaFile.file()).apply(javaFile)).isTrue();
    assertThat(predicates.is(new File("foo.php")).apply(javaFile)).isFalse();
  }

  @Test
  public void has_language() {
    assertThat(predicates.hasLanguage("java").apply(javaFile)).isTrue();
    assertThat(predicates.hasLanguage("php").apply(javaFile)).isFalse();
  }

  @Test
  public void has_languages() {
    assertThat(predicates.hasLanguages(Arrays.asList("java", "php")).apply(javaFile)).isTrue();
    assertThat(predicates.hasLanguages("java", "php").apply(javaFile)).isTrue();
    assertThat(predicates.hasLanguages(Arrays.asList("cobol", "php")).apply(javaFile)).isFalse();
    assertThat(predicates.hasLanguages("cobol", "php").apply(javaFile)).isFalse();
    assertThat(predicates.hasLanguages(Collections.<String>emptyList()).apply(javaFile)).isTrue();
  }

  @Test
  public void has_type() {
    assertThat(predicates.hasType(InputFile.Type.MAIN).apply(javaFile)).isTrue();
    assertThat(predicates.hasType(InputFile.Type.TEST).apply(javaFile)).isFalse();
  }

  @Test
  public void has_status() {
    assertThat(predicates.hasAnyStatus().apply(javaFile)).isTrue();
    try {
      predicates.hasStatus(InputFile.Status.SAME).apply(javaFile);
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Test
  public void not() {
    assertThat(predicates.not(predicates.hasType(InputFile.Type.MAIN)).apply(javaFile)).isFalse();
    assertThat(predicates.not(predicates.hasType(InputFile.Type.TEST)).apply(javaFile)).isTrue();
  }

  @Test
  public void and() {
    // empty
    assertThat(predicates.and().apply(javaFile)).isTrue();
    assertThat(predicates.and().apply(javaFile)).isTrue();
    assertThat(predicates.and(Collections.<FilePredicate>emptyList()).apply(javaFile)).isTrue();

    // two arguments
    assertThat(predicates.and(predicates.all(), predicates.all()).apply(javaFile)).isTrue();
    assertThat(predicates.and(predicates.all(), predicates.none()).apply(javaFile)).isFalse();
    assertThat(predicates.and(predicates.none(), predicates.all()).apply(javaFile)).isFalse();

    // collection
    assertThat(predicates.and(Arrays.asList(predicates.all(), predicates.all())).apply(javaFile)).isTrue();
    assertThat(predicates.and(Arrays.asList(predicates.all(), predicates.none())).apply(javaFile)).isFalse();

    // array
    assertThat(predicates.and(new FilePredicate[] {predicates.all(), predicates.all()}).apply(javaFile)).isTrue();
    assertThat(predicates.and(new FilePredicate[] {predicates.all(), predicates.none()}).apply(javaFile)).isFalse();
  }

  @Test
  public void or() {
    // empty
    assertThat(predicates.or().apply(javaFile)).isTrue();
    assertThat(predicates.or().apply(javaFile)).isTrue();
    assertThat(predicates.or(Collections.<FilePredicate>emptyList()).apply(javaFile)).isTrue();

    // two arguments
    assertThat(predicates.or(predicates.all(), predicates.all()).apply(javaFile)).isTrue();
    assertThat(predicates.or(predicates.all(), predicates.none()).apply(javaFile)).isTrue();
    assertThat(predicates.or(predicates.none(), predicates.all()).apply(javaFile)).isTrue();
    assertThat(predicates.or(predicates.none(), predicates.none()).apply(javaFile)).isFalse();

    // collection
    assertThat(predicates.or(Arrays.asList(predicates.all(), predicates.all())).apply(javaFile)).isTrue();
    assertThat(predicates.or(Arrays.asList(predicates.all(), predicates.none())).apply(javaFile)).isTrue();
    assertThat(predicates.or(Arrays.asList(predicates.none(), predicates.none())).apply(javaFile)).isFalse();

    // array
    assertThat(predicates.or(new FilePredicate[] {predicates.all(), predicates.all()}).apply(javaFile)).isTrue();
    assertThat(predicates.or(new FilePredicate[] {predicates.all(), predicates.none()}).apply(javaFile)).isTrue();
    assertThat(predicates.or(new FilePredicate[] {predicates.none(), predicates.none()}).apply(javaFile)).isFalse();
  }
}

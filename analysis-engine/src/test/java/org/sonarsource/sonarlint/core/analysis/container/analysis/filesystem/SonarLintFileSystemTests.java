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
package org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.commons.Language;
import testutils.TestInputFileBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SonarLintFileSystemTests {

  private SonarLintFileSystem fs;
  @TempDir
  Path basedir;
  private final InputFileIndex inputFileCache = new InputFileIndex();

  @BeforeEach
  void prepare() throws Exception {
    fs = new SonarLintFileSystem(AnalysisConfiguration.builder().setBaseDir(basedir).build(), inputFileCache);
  }

  @Test
  void return_fake_workdir() throws IOException {
    assertThat(fs.workDir()).isEqualTo(basedir.toFile());
  }

  @Test
  void add_languages() {
    assertThat(fs.languages()).isEmpty();

    inputFileCache.doAdd(new TestInputFileBuilder("src/Foo.php").setLanguage(Language.PHP).build());
    inputFileCache.doAdd(new TestInputFileBuilder("src/Bar.java").setLanguage(Language.JAVA).build());

    assertThat(fs.languages()).containsOnly("java", "php");
  }

  @Test
  void files() {
    assertThat(fs.inputFiles(fs.predicates().all())).isEmpty();

    var inputFile = new TestInputFileBuilder("src/Foo.php").setBaseDir(basedir).setLanguage(Language.PHP).build();
    inputFileCache.doAdd(inputFile);
    inputFileCache.doAdd(new TestInputFileBuilder("src/Bar.java").setBaseDir(basedir).setLanguage(Language.JAVA).build());
    inputFileCache.doAdd(new TestInputFileBuilder("src/Baz.java").setBaseDir(basedir).setLanguage(Language.JAVA).build());

    // no language
    inputFileCache.doAdd(new TestInputFileBuilder("src/readme.txt").setBaseDir(basedir).build());

    // needed for CFamily
    assertThat(fs.inputFile(fs.predicates().is(inputFile.file()))).isNotNull();

    assertThat(fs.inputFile(fs.predicates().hasURI(new File(basedir.toFile(), "src/Bar.java").toURI()))).isNotNull();
    assertThat(fs.inputFile(fs.predicates().hasURI(new File(basedir.toFile(), "does/not/exist").toURI()))).isNull();
    assertThat(fs.inputFile(fs.predicates().hasURI(new File(basedir.toFile(), "../src/Bar.java").toURI()))).isNull();

    assertThat(fs.files(fs.predicates().all())).hasSize(4);
    assertThat(fs.files(fs.predicates().hasLanguage("java"))).hasSize(2);
    assertThat(fs.files(fs.predicates().hasLanguage("cobol"))).isEmpty();

    assertThat(fs.hasFiles(fs.predicates().all())).isTrue();
    assertThat(fs.hasFiles(fs.predicates().hasLanguage("java"))).isTrue();
    assertThat(fs.hasFiles(fs.predicates().hasLanguage("cobol"))).isFalse();

    assertThat(fs.inputFiles(fs.predicates().all())).hasSize(4);
    assertThat(fs.inputFiles(fs.predicates().hasLanguage("php"))).hasSize(1);
    assertThat(fs.inputFiles(fs.predicates().hasLanguage("java"))).hasSize(2);
    assertThat(fs.inputFiles(fs.predicates().hasLanguage("cobol"))).isEmpty();

    assertThat(fs.languages()).containsOnly("java", "php");
  }

  @Test
  void input_file_returns_null_if_file_not_found() {
    assertThat(fs.inputFile(fs.predicates().hasLanguage("cobol"))).isNull();
  }

  @Test
  void input_file_fails_if_too_many_results() {
    inputFileCache.doAdd(new TestInputFileBuilder("src/Bar.java").setLanguage(Language.JAVA).build());
    inputFileCache.doAdd(new TestInputFileBuilder("src/Baz.java").setLanguage(Language.JAVA).build());

    var thrown = assertThrows(IllegalArgumentException.class, () -> fs.inputFile(fs.predicates().all()));
    assertThat(thrown).hasMessageStartingWith("expected one element");
  }

  @Test
  void input_file_supports_non_indexed_predicates() {
    inputFileCache.doAdd(new TestInputFileBuilder("src/Bar.java").setLanguage(Language.JAVA).build());

    // it would fail if more than one java file
    assertThat(fs.inputFile(fs.predicates().hasLanguage("java"))).isNotNull();
  }

  @Test
  void unsupported_resolve_path() {
    assertThrows(UnsupportedOperationException.class, () -> fs.resolvePath("foo"));
  }

}

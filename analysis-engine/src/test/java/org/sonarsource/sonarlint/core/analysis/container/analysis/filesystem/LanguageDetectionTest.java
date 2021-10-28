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
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.resources.Language;
import org.sonarsource.sonarlint.core.analysis.container.global.MapSettings;
import testutils.TestInputFileBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LanguageDetectionTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void test_sanitizeExtension() throws Exception {
    assertThat(LanguageDetection.sanitizeExtension(".cbl")).isEqualTo("cbl");
    assertThat(LanguageDetection.sanitizeExtension(".CBL")).isEqualTo("cbl");
    assertThat(LanguageDetection.sanitizeExtension("CBL")).isEqualTo("cbl");
    assertThat(LanguageDetection.sanitizeExtension("cbl")).isEqualTo("cbl");
  }

  @Test
  public void search_by_file_extension() throws Exception {
    LanguageDetection detection = new LanguageDetection(new MapSettings(Map.of()).asConfig());

    assertThat(detection.language(newInputFile("Foo.java"))).isEqualTo(org.sonarsource.sonarlint.core.analysis.api.Language.JAVA);
    assertThat(detection.language(newInputFile("src/Foo.java"))).isEqualTo(org.sonarsource.sonarlint.core.analysis.api.Language.JAVA);
    assertThat(detection.language(newInputFile("Foo.JAVA"))).isEqualTo(org.sonarsource.sonarlint.core.analysis.api.Language.JAVA);
    assertThat(detection.language(newInputFile("Foo.jav"))).isEqualTo(org.sonarsource.sonarlint.core.analysis.api.Language.JAVA);
    assertThat(detection.language(newInputFile("Foo.Jav"))).isEqualTo(org.sonarsource.sonarlint.core.analysis.api.Language.JAVA);

    assertThat(detection.language(newInputFile("abc.abap"))).isEqualTo(org.sonarsource.sonarlint.core.analysis.api.Language.ABAP);
    assertThat(detection.language(newInputFile("abc.ABAP"))).isEqualTo(org.sonarsource.sonarlint.core.analysis.api.Language.ABAP);

    assertThat(detection.language(newInputFile("abc.truc"))).isNull();
    assertThat(detection.language(newInputFile("abap"))).isNull();
  }

  @Test
  public void should_not_fail_if_no_language() throws Exception {
    LanguageDetection detection = new LanguageDetection(new MapSettings(Map.of()).asConfig());
    assertThat(detection.language(newInputFile("Foo.blabla"))).isNull();
  }

  @Test
  public void fail_if_conflicting_language_suffix() throws Exception {
    MapSettings settings = new MapSettings(Map.of(org.sonarsource.sonarlint.core.analysis.api.Language.XML.getFileSuffixesPropKey(), "xhtml",
      org.sonarsource.sonarlint.core.analysis.api.Language.HTML.getFileSuffixesPropKey(), "xhtml"));
    LanguageDetection detection = new LanguageDetection(settings.asConfig());
    InputFile newInputFile = newInputFile("abc.xhtml");
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> detection.language(newInputFile));
    assertThat(e.getMessage())
      .contains("Language of file 'file://")
      .contains("abc.xhtml' can not be decided as the file extension matches both ")
      .contains("HTML: xhtml")
      .contains("XML: xhtml");
  }

  private InputFile newInputFile(String path) throws IOException {
    File basedir = temp.newFolder();
    return new TestInputFileBuilder(path).setBaseDir(basedir.toPath()).build();
  }

  static class MockLanguage implements Language {
    private final String key;
    private final String[] extensions;

    MockLanguage(String key, String... extensions) {
      this.key = key;
      this.extensions = extensions;
    }

    @Override
    public String getKey() {
      return key;
    }

    @Override
    public String getName() {
      return key;
    }

    @Override
    public String[] getFileSuffixes() {
      return extensions;
    }
  }
}

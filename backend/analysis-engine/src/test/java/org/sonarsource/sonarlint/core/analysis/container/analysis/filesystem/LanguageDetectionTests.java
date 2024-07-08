/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.resources.Language;
import org.sonar.api.utils.MessageException;
import org.sonarsource.sonarlint.core.analysis.sonarapi.MapSettings;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import testutils.TestInputFileBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LanguageDetectionTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  private Path basedir;

  @Test
  void test_sanitizeExtension() {
    assertThat(LanguageDetection.sanitizeExtension(".cbl")).isEqualTo("cbl");
    assertThat(LanguageDetection.sanitizeExtension(".CBL")).isEqualTo("cbl");
    assertThat(LanguageDetection.sanitizeExtension("CBL")).isEqualTo("cbl");
    assertThat(LanguageDetection.sanitizeExtension("cbl")).isEqualTo("cbl");
  }

  @Test
  void search_by_file_extension() throws Exception {
    var detection = new LanguageDetection(new MapSettings(Map.of()).asConfig());

    assertThat(detection.language(newInputFile("Foo.java"))).isEqualTo(SonarLanguage.JAVA);
    assertThat(detection.language(newInputFile("src/Foo.java"))).isEqualTo(SonarLanguage.JAVA);
    assertThat(detection.language(newInputFile("Foo.JAVA"))).isEqualTo(SonarLanguage.JAVA);
    assertThat(detection.language(newInputFile("Foo.jav"))).isEqualTo(SonarLanguage.JAVA);
    assertThat(detection.language(newInputFile("Foo.Jav"))).isEqualTo(SonarLanguage.JAVA);

    assertThat(detection.language(newInputFile("abc.abap"))).isEqualTo(SonarLanguage.ABAP);
    assertThat(detection.language(newInputFile("abc.ABAP"))).isEqualTo(SonarLanguage.ABAP);

    assertThat(detection.language(newInputFile("abc.truc"))).isNull();
    assertThat(detection.language(newInputFile("abap"))).isNull();
  }

  @Test
  void recognise_yaml_files() throws IOException {
    var detection = new LanguageDetection(new MapSettings(Map.of()).asConfig());

    assertThat(detection.language(newInputFile("lambda.yaml"))).isEqualTo(SonarLanguage.YAML);
    assertThat(detection.language(newInputFile("lambda.yml"))).isEqualTo(SonarLanguage.YAML);
    assertThat(detection.language(newInputFile("config/lambda.yml"))).isEqualTo(SonarLanguage.YAML);
    assertThat(detection.language(newInputFile("config/lambda.YAML"))).isEqualTo(SonarLanguage.YAML);

    assertThat(detection.language(newInputFile("wrong.ylm"))).isNull();
    assertThat(detection.language(newInputFile("config.js"))).isNotEqualTo(SonarLanguage.YAML);
  }

  @Test
  void recognise_kts_files() throws IOException {
    var detection = new LanguageDetection(new MapSettings(Map.of()).asConfig());

    assertThat(detection.language(newInputFile("settings.kts"))).isEqualTo(SonarLanguage.KOTLIN);

    assertThat(detection.language(newInputFile("settings.kms"))).isNull();
    assertThat(detection.language(newInputFile("settings.js"))).isNotEqualTo(SonarLanguage.KOTLIN);
  }

  @Test
  void recognise_css_files() throws IOException {
    var detection = new LanguageDetection(new MapSettings(Map.of()).asConfig());

    assertThat(detection.language(newInputFile("style.css"))).isEqualTo(SonarLanguage.CSS);
    assertThat(detection.language(newInputFile("style.less"))).isEqualTo(SonarLanguage.CSS);
    assertThat(detection.language(newInputFile("style.scss"))).isEqualTo(SonarLanguage.CSS);

    assertThat(detection.language(newInputFile("style.stylus"))).isNull();
  }

  @Test
  void recognise_go_file() throws IOException {
    var detection = new LanguageDetection(new MapSettings(Map.of()).asConfig());

    assertThat(detection.language(newInputFile("myFile.go"))).isEqualTo(SonarLanguage.GO);
    assertThat(detection.language(newInputFile("folder/myFile.go"))).isEqualTo(SonarLanguage.GO);

    assertThat(detection.language(newInputFile("style.nogo"))).isNull();
  }

  @Test
  void recognise_terraform_file() throws IOException {
    var detection = new LanguageDetection(new MapSettings(Map.of()).asConfig());

    assertThat(detection.language(newInputFile("myFile.tf"))).isEqualTo(SonarLanguage.TERRAFORM);
    assertThat(detection.language(newInputFile("folder/myFile.tf"))).isEqualTo(SonarLanguage.TERRAFORM);

    assertThat(detection.language(newInputFile("style.notf"))).isNull();
  }

  @Test
  void should_not_fail_if_no_language() throws Exception {
    var detection = new LanguageDetection(new MapSettings(Map.of()).asConfig());
    assertThat(detection.language(newInputFile("Foo.blabla"))).isNull();
  }

  @Test
  void fail_if_conflicting_language_suffix() throws Exception {
    var settings = new MapSettings(Map.of(SonarLanguage.XML.getFileSuffixesPropKey(), "xhtml",
      SonarLanguage.HTML.getFileSuffixesPropKey(), "xhtml"));
    var detection = new LanguageDetection(settings.asConfig());
    var inputFile = newInputFile("abc.xhtml");
    var e = assertThrows(MessageException.class, () -> detection.language(inputFile));
    assertThat(e.getMessage())
      .contains("Language of file \"file://")
      .contains("abc.xhtml\" can not be decided as the file extension matches both ")
      .contains("HTML: xhtml")
      .contains("XML: xhtml");
  }

  private InputFile newInputFile(String path) {
    return new TestInputFileBuilder(path).setBaseDir(basedir).build();
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

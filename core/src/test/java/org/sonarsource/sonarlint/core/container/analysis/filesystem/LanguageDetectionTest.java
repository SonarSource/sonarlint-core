/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.analysis.filesystem;

import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.resources.Language;
import org.sonar.api.resources.Languages;
import org.sonar.api.utils.MessageException;

import static junit.framework.Assert.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

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
    LanguagesRepository languages = new DefaultLanguagesRepository(new Languages(new MockLanguage("java", "java", "jav"), new MockLanguage("cobol", "cbl", "cob")));
    LanguageDetection detection = new LanguageDetection(languages);

    assertThat(detection.language(newInputFile("Foo.java"))).isEqualTo("java");
    assertThat(detection.language(newInputFile("src/Foo.java"))).isEqualTo("java");
    assertThat(detection.language(newInputFile("Foo.JAVA"))).isEqualTo("java");
    assertThat(detection.language(newInputFile("Foo.jav"))).isEqualTo("java");
    assertThat(detection.language(newInputFile("Foo.Jav"))).isEqualTo("java");

    assertThat(detection.language(newInputFile("abc.cbl"))).isEqualTo("cobol");
    assertThat(detection.language(newInputFile("abc.CBL"))).isEqualTo("cobol");

    assertThat(detection.language(newInputFile("abc.php"))).isNull();
    assertThat(detection.language(newInputFile("abc"))).isNull();
  }

  @Test
  public void should_not_fail_if_no_language() throws Exception {
    LanguageDetection detection = spy(new LanguageDetection(new DefaultLanguagesRepository(new Languages())));
    assertThat(detection.language(newInputFile("Foo.java"))).isNull();
  }

  @Test
  public void plugin_can_declare_a_file_extension_twice_for_case_sensitivity() throws Exception {
    LanguagesRepository languages = new DefaultLanguagesRepository(new Languages(new MockLanguage("abap", "abap", "ABAP")));

    LanguageDetection detection = new LanguageDetection(languages);
    assertThat(detection.language(newInputFile("abc.abap"))).isEqualTo("abap");
  }

  @Test
  public void fail_if_conflicting_language_suffix() throws Exception {
    LanguagesRepository languages = new DefaultLanguagesRepository(new Languages(new MockLanguage("xml", "xhtml"), new MockLanguage("web", "xhtml")));
    LanguageDetection detection = new LanguageDetection(languages);
    try {
      detection.language(newInputFile("abc.xhtml"));
      fail();
    } catch (MessageException e) {
      assertThat(e.getMessage())
        .contains("Language of file 'abc.xhtml' can not be decided as the file matches patterns of both ")
        .contains("web: file:**/*.xhtml")
        .contains("xml: file:**/*.xhtml");
    }
  }

  private InputFile newInputFile(String path) throws IOException {
    File basedir = temp.newFolder();
    return new DefaultInputFile("foo", path).setModuleBaseDir(basedir.toPath());
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

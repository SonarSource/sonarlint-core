/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.analysis.container.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SonarLintPathPatternTests {

  @Test
  void constructor_should_add_double_star_prefix_when_not_present() {
    assertThat(new SonarLintPathPattern("*.java")).hasToString("**/*.java");
  }

  @Test
  void constructor_should_not_add_double_star_prefix_when_already_present() {
    assertThat(new SonarLintPathPattern("**/*.java")).hasToString("**/*.java");
  }

  @Test
  void create_should_return_array_of_patterns() {
    var patterns = SonarLintPathPattern.create(new String[]{"*.java", "*.xml"});
    
    assertThat(patterns).hasSize(2);
    assertThat(patterns[0].toString()).hasToString("**/*.java");
    assertThat(patterns[1].toString()).hasToString("**/*.xml");
  }

  @Test
  void create_should_return_empty_array_when_input_is_empty() {
    assertThat(SonarLintPathPattern.create(new String[]{})).isEmpty();
  }

  @Test
  void match_should_match_java_files() {
    var pattern = new SonarLintPathPattern("*.java");
    
    assertThat(pattern.match("src/main/java/Test.java")).isTrue();
    assertThat(pattern.match("src/test/java/Test.java")).isTrue();
    assertThat(pattern.match("Test.java")).isTrue();
    assertThat(pattern.match("Test.txt")).isFalse();
  }

  @Test
  void match_should_match_xml_files() {
    var pattern = new SonarLintPathPattern("*.xml");
    
    assertThat(pattern.match("pom.xml")).isTrue();
    assertThat(pattern.match("src/main/resources/config.xml")).isTrue();
    assertThat(pattern.match("Test.java")).isFalse();
  }

  @Test
  void match_should_match_with_path_patterns() {
    var pattern = new SonarLintPathPattern("src/**/*.java");
    
    assertThat(pattern.match("src/main/java/Test.java")).isTrue();
    assertThat(pattern.match("src/test/java/Test.java")).isTrue();
    assertThat(pattern.match("Test.java")).isFalse();
  }

  @Test
  void match_should_match_test_patterns() {
    var pattern = new SonarLintPathPattern("**/test/**/*.java");
    
    assertThat(pattern.match("src/test/java/Test.java")).isTrue();
    assertThat(pattern.match("src/main/java/Test.java")).isFalse();
  }

  @Test
  void match_with_case_sensitive_should_respect_case() {
    var pattern = new SonarLintPathPattern("*.JAVA");
    
    assertThat(pattern.match("src/main/java/Test.java", true)).isFalse();
    assertThat(pattern.match("src/main/java/Test.JAVA", true)).isTrue();
  }

  @Test
  void match_should_handle_different_path_separators() {
    var pattern = new SonarLintPathPattern("*.java");
    
    assertThat(pattern.match("src\\main\\java\\Test.java")).isTrue();
    assertThat(pattern.match("src/main/java/Test.java")).isTrue();
    assertThat(pattern.match("src\\test\\java\\Test.java")).isTrue();
    assertThat(pattern.match("Test.java")).isTrue();
  }

  @Test
  void match_should_handle_path_without_extension() {
    var pattern = new SonarLintPathPattern("*.java");
    
    var result = pattern.match("src/main/java/Test");
    
    assertThat(result).isFalse();
  }

  @Test
  void match_should_handle_path_with_dot_but_no_extension() {
    var pattern = new SonarLintPathPattern("*.java");
    
    var result = pattern.match("src/main/java/Test.");
    
    assertThat(result).isFalse();
  }

  @Test
  void toString_should_return_pattern_string() {
    var pattern = new SonarLintPathPattern("*.java");
    
    var result = pattern.toString();
    
    assertThat(result).isEqualTo("**/*.java");
  }

  @Test
  void sanitizeExtension_should_handle_null() {
    assertThat(SonarLintPathPattern.sanitizeExtension(null)).isNull();
  }

  @Test
  void sanitizeExtension_should_handle_empty_string() {
    assertThat(SonarLintPathPattern.sanitizeExtension("")).isEmpty();
  }

  @Test
  void sanitizeExtension_should_remove_leading_dot() {
    assertThat(SonarLintPathPattern.sanitizeExtension(".java")).isEqualTo("java");
  }

  @Test
  void sanitizeExtension_should_convert_to_lowercase() {
    assertThat(SonarLintPathPattern.sanitizeExtension("JAVA")).isEqualTo("java");
  }

  @Test
  void sanitizeExtension_should_handle_extension_without_dot() {
    assertThat(SonarLintPathPattern.sanitizeExtension("java")).isEqualTo("java");
  }

  @Test
  void sanitizeExtension_should_handle_mixed_case_with_dot() {
    assertThat(SonarLintPathPattern.sanitizeExtension(".JaVa")).isEqualTo("java");
  }
} 

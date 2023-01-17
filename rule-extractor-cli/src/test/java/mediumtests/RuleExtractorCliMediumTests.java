/*
 * SonarLint Core - Rule Extractor - CLI
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
package mediumtests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rule.extractor.cli.RuleExtractorCli;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

class RuleExtractorCliMediumTests {
  public static final String SOME_JS_RULE_KEY = "javascript:S3799";
  private static Set<Path> allJars;
  private Path outputFile;
  private List<String> params;

  @BeforeAll
  static void loadJars() throws IOException {
    var dir = Paths.get("target/plugins/");
    allJars = Files.list(dir)
      .filter(x -> x.getFileName().toString().endsWith(".jar"))
      .collect(toSet());
  }

  @TempDir
  private Path outputDir;

  @BeforeEach
  void prepareOutputFile() {
    outputFile = outputDir.resolve("out.json");
    params = new ArrayList<>();
    params.add("--output");
    params.add(outputFile.toString());
    for (var jar : allJars) {
      params.add("-p");
      params.add(jar.toString());
    }
  }

  @Test
  void shouldDumpJavaAndJsRules() throws Exception {
    params.add("-l");
    params.add("java,js");
    var exitCode = RuleExtractorCli.execute(params.toArray(new String[0]));
    assertThat(exitCode).isZero();
    var json = Files.readString(outputFile, StandardCharsets.UTF_8);
    JsonAssertions.assertThatJson(json).inPath("*.key").isArray().contains("java:S2093", SOME_JS_RULE_KEY);
  }

  @Test
  void shouldDumpVbNetRules() throws Exception {
    params.add("-l");
    params.add("vbnet");
    var exitCode = RuleExtractorCli.execute(params.toArray(new String[0]));
    assertThat(exitCode).isZero();
    var json = Files.readString(outputFile, StandardCharsets.UTF_8);
    JsonAssertions.assertThatJson(json).inPath("*.key").isArray().contains("vbnet:S139");
  }
  @Test
  void shouldExcludeJsRulesIfLanguageNotEnabled() throws Exception {
    params.add("-l");
    params.add("java");
    var exitCode = RuleExtractorCli.execute(params.toArray(new String[0]));
    assertThat(exitCode).isZero();
    var json = Files.readString(outputFile, StandardCharsets.UTF_8);
    JsonAssertions.assertThatJson(json).inPath("*.key").isArray().doesNotContain(SOME_JS_RULE_KEY);
  }

}

/*
 * SonarLint Core - Medium Tests
 * Copyright (C) SonarSource Sàrl
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
package mediumtest.analysis.sensor;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.rule.RuleKey;

public class IssueThenResolutionSensor implements Sensor {

  static final RuleKey RULE_KEY = RuleKey.of("repo", "rule");
  private static final Pattern SONAR_RESOLVE = Pattern.compile("sonar-resolve", Pattern.CASE_INSENSITIVE);

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name("Issue then resolution sensor");
  }

  @Override
  public void execute(SensorContext context) {
    for (var inputFile : context.fileSystem().inputFiles(file -> true)) {
      processFile(context, inputFile);
    }
  }

  private static void processFile(SensorContext context, InputFile inputFile) {
    List<String> lines;
    try {
      lines = inputFile.contents().lines().toList();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    for (var i = 0; i < lines.size(); i++) {
      if (lines.get(i).contains("int unused")) {
        var issue = context.newIssue();
        issue
          .at(issue.newLocation()
            .on(inputFile)
            .at(inputFile.selectLine(i + 1))
            .message("Remove this unused variable"))
          .forRule(RULE_KEY)
          .save();
      }
    }
    pause();
    for (var i = 0; i < lines.size(); i++) {
      if (SONAR_RESOLVE.matcher(lines.get(i)).find()) {
        context.newIssueResolution()
          .on(inputFile)
          .at(inputFile.selectLine(i + 1))
          .forRules(List.of(RULE_KEY))
          .comment("resolved by sonar-resolve")
          .save();
      }
    }
    pause();
  }

  private static void pause() {
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}

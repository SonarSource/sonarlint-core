/*
 * Example Plugin for SonarQube
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
package org.sonarsource.plugins.example;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.rule.RuleKey;

public class OneIssuePerLineSensor implements Sensor {

  @Override
  public void describe(final SensorDescriptor descriptor) {
    descriptor.name("One Issue Per Line");
  }

  @Override
  public void execute(final SensorContext context) {
    for (InputFile f : context.fileSystem().inputFiles(context.fileSystem().predicates().all())) {
      for (int i = 1; i < f.lines(); i++) {
        NewIssue newIssue = context.newIssue();
        newIssue
          .forRule(RuleKey.of(FooLintRulesDefinition.KEY, "ExampleRule1"))
          .at(newIssue.newLocation().on(f).at(f.selectLine(i)).message("Issue at line " + i))
          .save();
      }
    }
  }

}

/*
 * Example Plugin with global extension
 * Copyright (C) 2009-2019 SonarSource SA
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

public class GlobalSensor implements Sensor {

  private GlobalExtension globalExtension;

  public GlobalSensor(GlobalExtension globalExtension) {
    this.globalExtension = globalExtension;
  }

  @Override
  public void describe(final SensorDescriptor descriptor) {
    descriptor.name("Global")
      .onlyOnLanguage(GlobalLanguage.LANGUAGE_KEY);
  }

  @Override
  public void execute(final SensorContext context) {
    for (InputFile f : context.fileSystem().inputFiles(context.fileSystem().predicates().all())) {
      NewIssue newIssue = context.newIssue();
      newIssue
        .forRule(RuleKey.of(GlobalRulesDefinition.KEY, GlobalRulesDefinition.RULE_KEY))
        .at(newIssue.newLocation().on(f).message("Issue number " + globalExtension.getAndInc()))
        .save();
    }
  }

}

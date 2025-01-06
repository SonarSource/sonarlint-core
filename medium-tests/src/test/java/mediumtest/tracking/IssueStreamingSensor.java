/*
 * SonarLint Core - Medium Tests
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
package mediumtest.tracking;

import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.rule.RuleKey;

public class IssueStreamingSensor implements Sensor {

  @Override
  public void describe(SensorDescriptor descriptor) {
    // no implementation needed for the current tests
  }

  @Override
  public void execute(SensorContext context) {
    raiseIssue(context, 1);
    pause(500);
    raiseIssue(context, 2);
    pause(500);
  }

  private void raiseIssue(SensorContext context, int issueNumber) {
    var newIssue = context.newIssue();
    var newIssueLocation = newIssue.newLocation();
    var firstFile = context.fileSystem().inputFiles(file -> true).iterator().next();
    newIssue
      .at(newIssueLocation
        .message("Issue " + issueNumber)
        .at(firstFile.newRange(1, 0, 1, 1))
        .on(firstFile))
      .forRule(RuleKey.of("repo", "rule")).save();
  }

  private void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}

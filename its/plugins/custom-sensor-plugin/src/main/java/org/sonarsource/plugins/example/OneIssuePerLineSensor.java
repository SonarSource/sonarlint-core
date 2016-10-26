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

package org.sonarsource.plugins.example;

import org.sonar.api.Plugin;

/**
 * This class is the entry point for all extensions. It is referenced in pom.xml.
 */
public class ExamplePlugin implements Plugin {

  @Override
  public void define(Context context) {
    context.addExtensions(FooLintRulesDefinition.class, OneIssuePerLineSensor.class);
  }
}

package org.sonarsource.plugins.example;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinitionXmlLoader;

public final class FooLintRulesDefinition implements RulesDefinition {

  private static final String PATH_TO_RULES_XML = "/example/foolint-rules.xml";

  protected static final String KEY = "foolint";
  protected static final String NAME = "FooLint";

  protected String rulesDefinitionFilePath() {
    return PATH_TO_RULES_XML;
  }

  @Override
  public void define(Context context) {
    NewRepository repository = context.createRepository(KEY, "java").setName(NAME);

    InputStream rulesXml = this.getClass().getResourceAsStream(rulesDefinitionFilePath());
    if (rulesXml != null) {
      RulesDefinitionXmlLoader rulesLoader = new RulesDefinitionXmlLoader();
      rulesLoader.load(repository, rulesXml, StandardCharsets.UTF_8.name());
    }

    repository.done();
  }

}

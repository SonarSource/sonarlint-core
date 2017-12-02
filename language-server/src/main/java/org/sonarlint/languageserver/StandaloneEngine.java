/*
 * SonarLint Language Server
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarlint.languageserver;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.lsp4j.services.LanguageClient;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

public class StandaloneEngine extends AbstractEngine {
  private final StandaloneSonarLintEngine engine;
  
  public StandaloneEngine(LanguageClient client, LogOutput logOutput, URL[] analyzers) {
    super(client, logOutput);

    StandaloneGlobalConfiguration.Builder builder = StandaloneGlobalConfiguration.builder()
          .setLogOutput(logOutput)
          .addPlugins(analyzers);
    this.engine = new StandaloneSonarLintEngineImpl(builder.build());
  }
  
  @Override
  public AnalysisResults analyze(URI uri, Path baseDir, Iterable<ClientInputFile> inputFiles, Map<String, String> analyzerProperties, IssueListener issueListener) {
    StandaloneAnalysisConfiguration configuration  = new StandaloneAnalysisConfiguration(baseDir, baseDir.resolve(".sonarlint"),
        inputFiles,
        analyzerProperties);

      debug("Standalone Analysis triggered on " + uri + " with configuration: \n" + configuration.toString());
      return engine.analyze(
        configuration,
        issueListener, logOutput, null);
  }

  @Override
  public void stop() {
    // TODO Auto-generated method stub
    
  }

  @Override
  public RuleDetails getRuleDetails(String ruleKey) {
    return engine.getRuleDetails(ruleKey);
  }
}

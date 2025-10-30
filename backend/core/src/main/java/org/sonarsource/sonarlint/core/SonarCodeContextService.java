/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.monitoring.DogfoodEnvironmentDetectionService;
import org.sonarsource.sonarlint.core.commons.util.git.ProcessWrapperFactory;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedWithBindingEvent;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.springframework.context.event.EventListener;

/**
 * Dogfooding-only integration that runs SonarCodeContext CLI on repository open in connected mode.
 * Commands executed (in order): init, generate-md-guidelines, merge-md, install.
 * Outputs are expected under the '.sonar-code-context' directory.
 */
public class SonarCodeContextService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String SONAR_CODE_CONTEXT_DIR = ".sonar-code-context";
  private static final String CLI_EXECUTABLE = "sonar-code-context";
  private static final String SONAR_MD_FILENAME = "SONAR.md";
  private static final String CURSOR_MDC_FILENAME = "sonar-code-context.mdc";

  private final ClientFileSystemService clientFileSystemService;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final SonarProjectBranchTrackingService branchTrackingService;
  private final SonarLintRpcClient client;
  private final ProcessWrapperFactory processWrapperFactory = new ProcessWrapperFactory();
  private final boolean isEnabled;

  private final Set<String> initializedScopes = new HashSet<>();
  private final Set<String> mdcInstalledScopes = new HashSet<>();

  public SonarCodeContextService(DogfoodEnvironmentDetectionService dogfoodEnvDetectionService,
    ClientFileSystemService clientFileSystemService,
    ConfigurationRepository configurationRepository,
    ConnectionConfigurationRepository connectionConfigurationRepository,
    SonarProjectBranchTrackingService branchTrackingService,
    SonarLintRpcClient client, InitializeParams params) {
    this.clientFileSystemService = clientFileSystemService;
    this.configurationRepository = configurationRepository;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.branchTrackingService = branchTrackingService;
    this.client = client;
    this.isEnabled = dogfoodEnvDetectionService.isDogfoodEnvironment()
      && params.getBackendCapabilities().contains(BackendCapability.CONTEXT_GENERATION);
  }

  @EventListener
  public void onConfigurationScopesAdded(ConfigurationScopesAddedWithBindingEvent event) {
    if (!isEnabled) {
      return;
    }

    for (var configScopeId : event.getConfigScopeIds()) {
      var baseDir = clientFileSystemService.getBaseDir(configScopeId);
      // Only run for scopes that are directly bound (not inherited from parent)
      var bindingOpt = configurationRepository.getConfiguredBinding(configScopeId);
      if (baseDir != null && bindingOpt.isPresent()) {
        handleGeneration(configScopeId, baseDir, bindingOpt.get());
      } else {
        LOG.debug("No baseDir for configuration scope '{}' - skipping SonarCodeContext CLI", configScopeId);
      }
    }
  }

  @EventListener
  public void onBindingChanged(BindingConfigChangedEvent event) {
    if (!isEnabled) {
      return;
    }

    var configScopeId = event.configScopeId();
    var baseDir = clientFileSystemService.getBaseDir(configScopeId);
    var bindingOpt = configurationRepository.getConfiguredBinding(configScopeId);
    if (baseDir != null && bindingOpt.isPresent()) {
      handleGeneration(configScopeId, baseDir, bindingOpt.get());
    }
  }

  private void handleGeneration(String configScopeId, Path baseDir, Binding binding) {
    var paramsOpt = prepareCliParams(binding, configScopeId);
    if (paramsOpt.isPresent()) {
      var workingDir = computeWorkingBaseDir(baseDir);
      if (initializedScopes.add(configScopeId)) {
        runInit(workingDir);
      }
      runGenerateGuidelines(workingDir, paramsOpt.get());
      runMergeMd(workingDir);
      if (mdcInstalledScopes.add(configScopeId)) {
        runInstall(workingDir);
      }
    } else {
      LOG.debug("Missing parameters for SonarCodeContext CLI, skipping for configuration scope '{}'", configScopeId);
    }
  }

  private Optional<CliParams> prepareCliParams(Binding binding, String configScopeId) {
    var connection = connectionConfigurationRepository.getConnectionById(binding.connectionId());
    if (connection == null) {
      return Optional.empty();
    }
    var url = connection.getUrl();
    var token = getTokenForConnection(binding.connectionId());
    if (token.isEmpty()) {
      return Optional.empty();
    }
    var branch = branchTrackingService.awaitEffectiveSonarProjectBranch(configScopeId).orElse(null);
    return Optional.of(new CliParams(url, token.get(), binding.sonarProjectKey(), branch));
  }

  private Optional<String> getTokenForConnection(String connectionId) {
    try {
      var creds = client.getCredentials(new GetCredentialsParams(connectionId)).join().getCredentials();
      if (creds != null && creds.isLeft()) {
        var tokenDto = creds.getLeft();
        return Optional.ofNullable(tokenDto.getToken());
      }
      return Optional.empty();
    } catch (Exception e) {
      LOG.debug("Unable to retrieve token for connection '{}'", connectionId, e);
      return Optional.empty();
    }
  }

  private void runInit(Path baseDir) {
    var command = new ArrayList<>(List.of(resolveCliExecutable(), "init"));
    execute(baseDir, command);
    var settings = baseDir.resolve(SONAR_CODE_CONTEXT_DIR).resolve("settings.json");
    if (Files.exists(settings)) {
      LOG.debug("Initialized SonarCodeContext settings at {}", settings);
    }
  }

  private void runGenerateGuidelines(Path baseDir, CliParams params) {
    var command = new ArrayList<>(List.of(
      resolveCliExecutable(),
      "generate-md-guidelines",
      "--sq-url=" + params.sqUrl,
      "--sq-token=" + params.sqToken,
      "--sq-project-key=" + params.projectKey
    ));
    if (params.sqBranch() != null) {
      command.add("--sq-branch=" + params.sqBranch());
    }
    execute(baseDir, command);
  }

  private void runMergeMd(Path baseDir) {
    var command = new ArrayList<>(List.of(resolveCliExecutable(), "merge-md"));
    execute(baseDir, command);
    var merged = baseDir.resolve(SONAR_CODE_CONTEXT_DIR).resolve(SONAR_MD_FILENAME);
    if (Files.exists(merged)) {
      LOG.debug("Merged {} at {}", SONAR_MD_FILENAME, merged);
    } else {
      LOG.debug("{} was not generated under {}", SONAR_MD_FILENAME, baseDir.resolve(SONAR_CODE_CONTEXT_DIR));
    }
  }

  private void runInstall(Path baseDir) {
    var command = new ArrayList<>(List.of(resolveCliExecutable(), "install", "--force", "--cursor-mdc"));
    execute(baseDir, command);
    var cursorRule = baseDir.resolve(".cursor").resolve("rules").resolve(CURSOR_MDC_FILENAME);
    if (Files.exists(cursorRule)) {
      LOG.debug("Generated {} at {}", CURSOR_MDC_FILENAME, cursorRule);
    }
  }

  private void execute(Path baseDir, List<String> command) {
    var result = processWrapperFactory.create(baseDir, LOG::debug, command.toArray(new String[0])).execute();
    if (result.exitCode() != 0) {
      LOG.debug("Command '{}' exited with code {} in {}", String.join(" ", command), result.exitCode(), baseDir);
    }
  }

  private record CliParams(String sqUrl, String sqToken, String projectKey, @Nullable String sqBranch) {}

  private static Path computeWorkingBaseDir(Path baseDir) {
    try {
      var current = baseDir;
      while (current != null) {
        if (Files.isDirectory(current.resolve(".git"))) {
          return current;
        }
        current = current.getParent();
      }
    } catch (Exception e) {
      // ignore and fallback
    }
    return baseDir;
  }

  private static String resolveCliExecutable() {
    // Used for testing
    var prop = System.getProperty("sonar.code.context.executable");
    if (prop != null && !prop.isBlank()) {
      return prop;
    }
    return CLI_EXECUTABLE;
  }

}

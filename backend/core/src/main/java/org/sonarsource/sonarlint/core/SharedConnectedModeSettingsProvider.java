package org.sonarsource.sonarlint.core;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.sonarsource.sonarlint.core.commons.SonarLintException;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;

public class SharedConnectedModeSettingsProvider {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private static final String SONARCLOUD_CONNECTED_MODE_CONFIG = """
              {
                "sonarCloudOrganization": "%s",
                "projectKey": "%s"
              }
          """;
  private static final String SONARQUBE_CONNECTED_MODE_CONFIG = """
        {
          "sonarQubeUri": "%s",
          "projectKey": "%s"
        }
    """;

  public SharedConnectedModeSettingsProvider(ConfigurationRepository configurationRepository, ConnectionConfigurationRepository connectionRepository) {
    this.configurationRepository = configurationRepository;
    this.connectionRepository = connectionRepository;
  }

  public String getSharedConnectedModeConfigFileContents(String configScopeId) {
    var bindingConfiguration = configurationRepository.getBindingConfiguration(configScopeId);
    if (bindingConfiguration != null && bindingConfiguration.isBound()) {
      var projectKey = bindingConfiguration.getSonarProjectKey();
      var connectionId = bindingConfiguration.getConnectionId();

      var connection =  Objects.requireNonNull(connectionRepository.getConnectionById(Objects.requireNonNull(connectionId)));
      if (connection.getEndpointParams().isSonarCloud()) {
        AtomicReference<String> fileContents = new AtomicReference<>();
        connection.getEndpointParams().getOrganization().ifPresent(organization ->
          fileContents.set(String.format(SONARCLOUD_CONNECTED_MODE_CONFIG, organization, projectKey))
        );
        return fileContents.get();
      } else {
        return String.format(SONARQUBE_CONNECTED_MODE_CONFIG, connection.getEndpointParams().getBaseUrl(), projectKey);
      }
    } else {
      LOG.warn("Request to generate shared Connected Mode configuration file content failed; Binding not yet available for '{}'", configScopeId);
      throw new SonarLintException("Binding not found; Cannot generate shared Connected Mode file contents");
    }
  }
}

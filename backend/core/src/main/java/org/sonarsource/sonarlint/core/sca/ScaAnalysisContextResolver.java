/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.sca;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.analysis.UserAnalysisPropertiesRepository;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.storage.StorageService;

/**
 * Resolves everything required to run a manual SCA analysis for a configuration scope (binding, base directory, scanner
 * endpoint, credentials, server version and scanner properties) from SLCore services and storage, so that
 * {@link ScaProjectAnalysisService} stays focused on orchestrating the analysis itself.
 */
public class ScaAnalysisContextResolver {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String SCA_DOWNLOAD_BASE_URL_PROPERTY = "sonarlint.sca.downloadBaseUrl";
  private static final String SONARCLOUD_SCA_SCANNER_CDN_URL = "https://scanner.sonarcloud.io/tidelift-cli";

  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final ClientFileSystemService clientFileSystemService;
  private final StorageService storageService;
  private final UserAnalysisPropertiesRepository userAnalysisPropertiesRepository;

  public ScaAnalysisContextResolver(ConfigurationRepository configurationRepository, ConnectionConfigurationRepository connectionRepository,
    SonarQubeClientManager sonarQubeClientManager, ClientFileSystemService clientFileSystemService, StorageService storageService,
    UserAnalysisPropertiesRepository userAnalysisPropertiesRepository) {
    this.configurationRepository = configurationRepository;
    this.connectionRepository = connectionRepository;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.clientFileSystemService = clientFileSystemService;
    this.storageService = storageService;
    this.userAnalysisPropertiesRepository = userAnalysisPropertiesRepository;
  }

  public AnalysisContext resolve(String configurationScopeId) {
    var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
    var connection = connectionRepository.getConnectionById(binding.connectionId());
    if (connection == null) {
      throw new ResponseErrorException(new ResponseError(SonarLintRpcErrorCode.CONNECTION_NOT_FOUND,
        "The provided configuration scope is bound to an unknown connection: " + binding.connectionId(), binding.connectionId()));
    }
    var baseDir = clientFileSystemService.getBaseDir(configurationScopeId);
    if (baseDir == null) {
      throw invalidArgument("No base directory is available for configuration scope '" + configurationScopeId + "'");
    }
    var endpoint = resolveScannerEndpoint(connection);
    var sonarToken = resolveSonarToken(binding);
    var sqServerVersion = resolveSqServerVersion(binding, connection);
    var scannerProperties = resolveScannerProperties(binding, configurationScopeId);
    return new AnalysisContext(binding, baseDir, endpoint, sonarToken, sqServerVersion, scannerProperties);
  }

  private static ScannerEndpoint resolveScannerEndpoint(AbstractConnectionConfiguration connection) {
    var endpointParams = connection.getEndpointParams();
    var apiBaseUrl = toScaApiBaseUrl(endpointParams);
    if (apiBaseUrl == null) {
      throw invalidArgument("Missing SCA API base URL");
    }
    var downloadBaseUrl = StringUtils.firstNonBlank(System.getProperty(SCA_DOWNLOAD_BASE_URL_PROPERTY), toScaDownloadBaseUrl(endpointParams));
    if (downloadBaseUrl == null) {
      throw invalidArgument("Missing SCA scanner download base URL. Provide it in the request or with system property '" + SCA_DOWNLOAD_BASE_URL_PROPERTY + "'");
    }
    return new ScannerEndpoint(apiBaseUrl, downloadBaseUrl, endpointParams.isSonarCloud());
  }

  private String resolveSonarToken(Binding binding) {
    var sonarQubeClient = sonarQubeClientManager.getValidClientOrThrow(binding.connectionId());
    var credentials = sonarQubeClient.getCredentials();
    return credentials.map(TokenDto::getToken, usernamePassword -> {
      throw invalidArgument("SCA project analysis requires token credentials");
    });
  }

  @CheckForNull
  private String resolveSqServerVersion(Binding binding, AbstractConnectionConfiguration connection) {
    if (connection.getEndpointParams().isSonarCloud()) {
      return null;
    }
    return storageService.connection(binding.connectionId()).serverInfo().read()
      .map(info -> info.version().getName())
      .orElse(null);
  }

  private Map<String, String> resolveScannerProperties(Binding binding, String configurationScopeId) {
    var properties = new HashMap<String, String>();
    try {
      properties.putAll(storageService.binding(binding).analyzerConfiguration().read().getSettings());
    } catch (RuntimeException e) {
      LOG.debug("No stored analyzer configuration for binding '{}', proceeding without server scanner properties: {}",
        binding.connectionId(), e.getMessage());
    }
    properties.putAll(userAnalysisPropertiesRepository.getUserProperties(configurationScopeId));
    return properties;
  }

  @CheckForNull
  private static String toScaApiBaseUrl(EndpointParams endpointParams) {
    if (endpointParams.isSonarCloud()) {
      var apiBaseUrl = endpointParams.getApiBaseUrl() == null ? endpointParams.getBaseUrl() : endpointParams.getApiBaseUrl();
      if (apiBaseUrl == null) {
        return null;
      }
      return ServerApiHelper.concat(apiBaseUrl, "/sca");
    }
    return ServerApiHelper.concat(endpointParams.getBaseUrl(), "/api/v2/sca");
  }

  private static String toScaDownloadBaseUrl(EndpointParams endpointParams) {
    if (endpointParams.isSonarCloud()) {
      return SONARCLOUD_SCA_SCANNER_CDN_URL;
    }
    return ServerApiHelper.concat(endpointParams.getBaseUrl(), "/api/v2/sca/clis");
  }

  private static ResponseErrorException invalidArgument(String message) {
    return new ResponseErrorException(new ResponseError(SonarLintRpcErrorCode.INVALID_ARGUMENT, message, null));
  }

  public record AnalysisContext(Binding binding, Path baseDir, ScannerEndpoint endpoint, String sonarToken,
    @Nullable String sqServerVersion, Map<String, String> scannerProperties) {
  }

  public record ScannerEndpoint(String apiBaseUrl, String downloadBaseUrl, boolean sonarCloud) {
  }
}

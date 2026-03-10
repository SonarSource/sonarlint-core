/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.spring;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.plugin.PluginArtifactProvider;
import org.sonarsource.sonarlint.core.plugin.ServerPluginsCache;
import org.sonarsource.sonarlint.core.plugin.ondemand.DownloadableArtifact;
import org.sonarsource.sonarlint.core.plugin.ondemand.OnDemandArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.ConnectedModeArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.EmbeddedArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.EmbeddedExtraArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.PremiumArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.UnsupportedArtifactResolver;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
  UnsupportedArtifactResolver.class,
  EmbeddedExtraArtifactResolver.class,
  EmbeddedArtifactResolver.class,
  OnDemandArtifactResolver.class,
  PremiumArtifactResolver.class,
  ServerPluginsCache.class,
  PluginArtifactProvider.class
})
class PluginsConfig {

  @Bean(name = "pluginDownloadExecutor", destroyMethod = "shutdownNow")
  ExecutorService pluginDownloadExecutor() {
    return Executors.newCachedThreadPool(r -> new Thread(r, "sonarlint-plugin-download"));
  }

  @Bean
  ConnectedModeArtifactResolver connectedModeArtifactResolver(
    StorageService storageService,
    ConnectionConfigurationRepository connectionConfigurationRepository,
    SonarQubeClientManager sonarQubeClientManager,
    ServerPluginsCache serverPluginsCache,
    ApplicationEventPublisher eventPublisher,
    InitializeParams initializeParams,
    @Qualifier("pluginDownloadExecutor") ExecutorService pluginDownloadExecutor) {
    return new ConnectedModeArtifactResolver(
      storageService, connectionConfigurationRepository, sonarQubeClientManager, serverPluginsCache, eventPublisher, pluginDownloadExecutor,
      initializeParams.getConnectedModeEmbeddedPluginPathsByKey().keySet());
  }

  @Bean
  Map<SonarLanguage, DownloadableArtifact> onDemandArtifactsByLanguage() {
    return Map.of(
      SonarLanguage.C, DownloadableArtifact.CFAMILY_PLUGIN,
      SonarLanguage.CPP, DownloadableArtifact.CFAMILY_PLUGIN,
      SonarLanguage.CS, DownloadableArtifact.CSHARP_OSS,
      SonarLanguage.VBNET, DownloadableArtifact.CSHARP_OSS);
  }
}

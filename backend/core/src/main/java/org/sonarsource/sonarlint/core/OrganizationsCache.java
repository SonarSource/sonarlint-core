/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.OrganizationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static org.sonarsource.sonarlint.core.commons.log.SonarLintLogger.singlePlural;

/**
 * Cache user organizations index for a certain amount of time.
 */
@Named
@Singleton
public class OrganizationsCache {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConnectionService connectionService;

  private final Cache<Either<TokenDto, UsernamePasswordDto>, TextSearchIndex<OrganizationDto>> textSearchIndexCacheByCredentials = CacheBuilder.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build();

  public OrganizationsCache(ConnectionService connectionService) {
    this.connectionService = connectionService;
  }

  public List<OrganizationDto> fuzzySearchOrganizations(Either<TokenDto, UsernamePasswordDto> credentials, String searchText, SonarLintCancelMonitor cancelMonitor) {
    return getTextSearchIndex(credentials, cancelMonitor).search(searchText)
      .entrySet()
      .stream()
      .sorted(Comparator.comparing(Map.Entry<OrganizationDto, Double>::getValue).reversed()
        .thenComparing(Comparator.comparing(e -> e.getKey().getName(), String.CASE_INSENSITIVE_ORDER)))
      .limit(10)
      .map(Map.Entry::getKey)
      .collect(Collectors.toList());
  }

  public TextSearchIndex<OrganizationDto> getTextSearchIndex(Either<TokenDto, UsernamePasswordDto> credentials, SonarLintCancelMonitor cancelMonitor) {
    try {
      return textSearchIndexCacheByCredentials.get(credentials, () -> {
        LOG.debug("Load user organizations...");
        List<OrganizationDto> orgs;
        try {
          orgs = connectionService.listUserOrganizations(credentials, cancelMonitor);
        } catch (Exception e) {
          LOG.error("Error while querying SonarCloud organizations", e);
          return new TextSearchIndex<>();
        }
        if (orgs.isEmpty()) {
          LOG.debug("No organizations found");
          return new TextSearchIndex<>();
        } else {
          LOG.debug("Creating index for {} {}", orgs.size(), singlePlural(orgs.size(), "organization", "organizations"));
          var index = new TextSearchIndex<OrganizationDto>();
          orgs.forEach(org -> index.index(org, org.getKey() + " " + org.getName()));
          return index;
        }
      });
    } catch (ExecutionException e) {
      throw new IllegalStateException(e.getCause());
    }
  }


}

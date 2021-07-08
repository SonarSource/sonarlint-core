/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.util.Set;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.storage.ActiveRulesStore;
import org.sonarsource.sonarlint.core.container.storage.RulesStore;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.rules.RulesApi;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerRules;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class RulesDownloader {
  private final RulesApi rulesApi;
  private final Set<Language> enabledLanguages;
  private final RulesStore rulesStore;
  private final ActiveRulesStore activeRulesStore;

  public RulesDownloader(ServerApiHelper serverApiHelper, ConnectedGlobalConfiguration globalConfiguration, RulesStore rulesStore, ActiveRulesStore activeRulesStore) {
    this.rulesApi = new ServerApi(serverApiHelper).rules();
    this.enabledLanguages = globalConfiguration.getEnabledLanguages();
    this.rulesStore = rulesStore;
    this.activeRulesStore = activeRulesStore;
  }

  public void fetchRules(ProgressWrapper progress) {
    ServerRules serverRules = rulesApi.getAll(enabledLanguages, progress);
    activeRulesStore.store(serverRules.getActiveRulesByQualityProfile());
    rulesStore.store(serverRules.getAll());
  }
}

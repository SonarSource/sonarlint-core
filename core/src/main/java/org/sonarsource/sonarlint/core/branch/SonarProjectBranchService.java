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
package org.sonarsource.sonarlint.core.branch;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Optional;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.event.ActiveSonarProjectBranchChanged;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.vcs.ActiveSonarProjectBranchRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidChangeActiveSonarProjectBranchParams;

@Named
@Singleton
public class SonarProjectBranchService {
  private final ActiveSonarProjectBranchRepository activeSonarProjectBranchRepository;
  private final ConfigurationRepository configurationRepository;
  private final EventBus eventBus;

  public SonarProjectBranchService(ActiveSonarProjectBranchRepository activeSonarProjectBranchRepository, ConfigurationRepository configurationRepository, EventBus eventBus) {
    this.activeSonarProjectBranchRepository = activeSonarProjectBranchRepository;
    this.configurationRepository = configurationRepository;
    this.eventBus = eventBus;
  }

  public void didChangeActiveSonarProjectBranch(DidChangeActiveSonarProjectBranchParams params) {
    var newActiveBranchName = params.getNewActiveBranchName();
    var configScopeId = params.getConfigScopeId();
    var oldBranchName = activeSonarProjectBranchRepository.setActiveBranchName(configScopeId, params.getNewActiveBranchName());
    if (!newActiveBranchName.equals(oldBranchName)) {
      eventBus.post(new ActiveSonarProjectBranchChanged(configScopeId, newActiveBranchName));
    }
  }

  public Optional<String> getEffectiveActiveSonarProjectBranch(String configurationScopeId) {
    var currentConfigScopeId = configurationScopeId;
    do {
      var configurationScope = configurationRepository.getConfigurationScope(currentConfigScopeId);
      if (configurationScope == null) {
        // the scope might have been deleted in the meantime
        break;
      }
      var maybeBranch = activeSonarProjectBranchRepository.getActiveSonarProjectBranch(currentConfigScopeId);
      if (maybeBranch.isPresent()) {
        return maybeBranch;
      }
      currentConfigScopeId = configurationScope.getParentId();
    } while (currentConfigScopeId != null);
    return Optional.empty();
  }

  @Subscribe
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent removedEvent) {
    var currentConfigScopeId = removedEvent.getRemovedConfigurationScopeId();
    activeSonarProjectBranchRepository.clearActiveProjectBranch(currentConfigScopeId);
  }

}

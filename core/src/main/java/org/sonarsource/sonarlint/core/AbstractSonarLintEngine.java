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
package org.sonarsource.sonarlint.core;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.container.global.ModuleRegistry;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleFileEventNotifier;
import org.sonarsource.sonarlint.core.client.api.common.AbstractAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.commons.pico.ComponentContainer;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractor;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

public abstract class AbstractSonarLintEngine implements SonarLintEngine {
  protected final ReadWriteLock rwl = new ReentrantReadWriteLock();

  protected abstract ModuleRegistry getModuleRegistry();

  private final ClientLogOutput logOutput;
  protected Map<String, SonarLintRuleDefinition> allRulesDefinitionsByKey;

  protected AbstractSonarLintEngine(@Nullable ClientLogOutput logOutput) {
    this.logOutput = logOutput;
  }

  @Override
  public void declareModule(ClientModuleInfo module) {
    withRwLock(() -> getModuleRegistry().registerModule(module));
  }

  @Override
  public void stopModule(Object moduleKey) {
    withRwLock(() -> {
      getModuleRegistry().unregisterModule(moduleKey);
      return null;
    });
  }

  @Override
  public void fireModuleFileEvent(Object moduleKey, ClientModuleFileEvent event) {
    withRwLock(() -> {
      ComponentContainer moduleContainer = getModuleRegistry().getContainerFor(moduleKey);
      if (moduleContainer != null) {
        moduleContainer.getComponentByType(ModuleFileEventNotifier.class).fireModuleFileEvent(event);
      }
      return null;
    });
  }

  protected void loadPluginMetadata(PluginInstancesRepository pluginInstancesRepository, Set<Language> enabledLanguages) {
    var ruleExtractor = new RulesDefinitionExtractor();
    allRulesDefinitionsByKey = ruleExtractor.extractRules(pluginInstancesRepository, enabledLanguages).stream()
      .collect(Collectors.toMap(SonarLintRuleDefinition::getKey, r -> r));
  }

  protected <T> T withModule(AbstractAnalysisConfiguration configuration, Function<ModuleContainer, T> consumer) {
    Object moduleKey = configuration.moduleKey();
    var moduleContainer = moduleKey != null ? getModuleRegistry().getContainerFor(moduleKey) : null;
    if (moduleContainer == null) {
      // if not found, means we are outside of any module (e.g. single file analysis on VSCode)
      moduleContainer = getModuleRegistry().createTranscientContainer(configuration.inputFiles());
    }
    Throwable originalException = null;
    try {
      return consumer.apply(moduleContainer);
    } catch (Throwable e) {
      originalException = e;
      throw e;
    } finally {
      try {
        if (moduleContainer.isTranscient()) {
          moduleContainer.stopComponents();
        }
      } catch (Exception e) {
        if (originalException != null) {
          e.addSuppressed(originalException);
        }
        throw e;
      }
    }
  }

  protected <T> T withRwLock(Supplier<T> callable) {
    setLogging(null);
    rwl.writeLock().lock();
    try {
      return callable.get();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      rwl.writeLock().unlock();
    }
  }

  protected void setLogging(@Nullable ClientLogOutput logOutput) {
    if (logOutput != null) {
      SonarLintLogger.setTarget(logOutput);
    } else {
      SonarLintLogger.setTarget(this.logOutput);
    }
  }

}

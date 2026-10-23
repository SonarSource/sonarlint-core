/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.command;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.sonarsource.sonarlint.core.analysis.AnalysisQueue;
import org.sonarsource.sonarlint.core.analysis.SchedulerResetConfiguration;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalAnalysisContainer;
import org.sonarsource.sonarlint.core.analysis.container.global.ModuleRegistry;

public class ResetPluginsCommand extends Command {

  private final Supplier<SchedulerResetConfiguration> schedulerResetConfigurationSupplier;
  private final AtomicReference<GlobalAnalysisContainer> globalAnalysisContainer;
  private final AnalysisQueue analysisQueue;

  public ResetPluginsCommand(AtomicReference<GlobalAnalysisContainer> globalAnalysisContainer, AnalysisQueue analysisQueue,
    Supplier<SchedulerResetConfiguration> schedulerResetConfigurationSupplier) {
    this.schedulerResetConfigurationSupplier = schedulerResetConfigurationSupplier;
    this.globalAnalysisContainer = globalAnalysisContainer;
    this.analysisQueue = analysisQueue;
  }

  @Override
  public void execute(ModuleRegistry moduleRegistry) {
    globalAnalysisContainer.get().stopComponents();
    var pluginsWithConfig = schedulerResetConfigurationSupplier.get();
    globalAnalysisContainer.set(new GlobalAnalysisContainer(pluginsWithConfig.config(), pluginsWithConfig.plugins()));
    globalAnalysisContainer.get().startComponents();
    analysisQueue.clearAllButAnalysesAndResets();
  }
}

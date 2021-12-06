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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.StateListener;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.plugin.common.log.LogOutput;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static java.util.Objects.requireNonNull;

public final class ConnectedSonarLintEngineImpl extends AbstractSonarLintEngine implements ConnectedSonarLintEngine {

  private static final Logger LOG = Loggers.get(ConnectedSonarLintEngineImpl.class);

  private final ConnectedGlobalConfiguration globalConfig;
  private final List<StateListener> stateListeners = new CopyOnWriteArrayList<>();
  private volatile State state = State.UNKNOWN;

  public ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration globalConfig) {
    super(globalConfig.getLogOutput());
    this.globalConfig = globalConfig;

    start();
  }

  @Override
  public State getState() {
    return state;
  }

  @Override
  public void addStateListener(StateListener listener) {
    stateListeners.add(listener);
  }

  @Override
  public void removeStateListener(StateListener listener) {
    stateListeners.remove(listener);
  }

  private void changeState(State state) {
    this.state = state;
    for (StateListener listener : stateListeners) {
      listener.stateChanged(state);
    }
  }

  public void start() {
  }

  @Override
  public AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, Consumer<Issue> issueListener, @Nullable LogOutput logOutput, @Nullable ProgressMonitor monitor) {
    requireNonNull(configuration);
    requireNonNull(issueListener);
    return withReadLock(() -> {
      setLogging(logOutput);
      return withModule(configuration, moduleContainer -> {
        try {
          return getHandler().analyze(moduleContainer, configuration, issueListener, new ProgressWrapper(monitor));
        } catch (RuntimeException e) {
          throw SonarLintWrappedException.wrap(e);
        }
      });

    });
  }

  @Override
  public void stop(boolean deleteStorage) {
    analysisEngine.stop();
  }

}

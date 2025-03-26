/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.sonarsource.sonarlint.core.analysis.api.TriggerType;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.Command;
import org.sonarsource.sonarlint.core.analysis.command.NotifyModuleEventCommand;
import org.sonarsource.sonarlint.core.analysis.command.RegisterModuleCommand;
import org.sonarsource.sonarlint.core.analysis.command.UnregisterModuleCommand;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static java.util.Map.entry;
import static org.sonarsource.sonarlint.core.analysis.AnalysisUtils.isSimilarAnalysis;

public class AnalysisQueue {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String ANALYSIS_EXPIRATION_DELAY_PROPERTY_NAME = "sonarqube.ide.internal.analysis.expiration.delay";
  private static final Duration ANALYSIS_EXPIRATION_DEFAULT_DELAY = Duration.ofMinutes(1);
  private final Duration analysisExpirationDelay = getAnalysisExpirationDelay();

  private final PriorityQueue<QueuedCommand> queue = new PriorityQueue<>(new CommandComparator());

  public synchronized void post(Command command) {
    cancelAndUnscheduleSimilarAnalysisCommands(command);

    queue.add(new QueuedCommand(command));
    notifyAll();
  }

  public synchronized void wakeUp() {
    notifyAll();
  }

  public synchronized List<Command> removeAll() {
    var pendingTasks = new ArrayList<>(queue);
    queue.clear();
    return pendingTasks.stream().map(QueuedCommand::getCommand).toList();
  }

  public synchronized Command takeNextCommand() throws InterruptedException {
    while (true) {
      var firstReadyCommand = pollNextReadyCommand();
      if (firstReadyCommand.isPresent()) {
        var command = firstReadyCommand.get();
        return tidyUp(command);
      }
      // wait for a new command to come in
      wait();
    }
  }

  public synchronized void clearAllButAnalyses() {
    removeAll(queuedCommand -> !(queuedCommand.command instanceof AnalyzeCommand));
  }

  private void cancelAndUnscheduleSimilarAnalysisCommands(Command command) {
    if (command instanceof AnalyzeCommand newAnalysis && newAnalysis.getTriggerType() == TriggerType.AUTO) {
      List<AnalyzeCommand> analyzeCommandsToRemove = new ArrayList<>();

      for (Command existingCommand : queue) {
        if (existingCommand instanceof AnalyzeCommand existingAnalysis &&
          existingAnalysis.getTriggerType() == TriggerType.AUTO &&
          isSimilarAnalysis(newAnalysis, existingAnalysis)) {
          analyzeCommandsToRemove.add(existingAnalysis);
        }
      }

      if (!analyzeCommandsToRemove.isEmpty()) {
        LOG.debug("Cancelling {} outdated automatic analysis commands", analyzeCommandsToRemove.size());
        queue.removeAll(analyzeCommandsToRemove);
        analyzeCommandsToRemove.forEach(AnalyzeCommand::cancel);
      }
    }
  }

  private Optional<QueuedCommand> pollNextReadyCommand() {
    var commandsToKeep = new ArrayList<QueuedCommand>();
    // cannot use iterator as priority order is not guaranteed
    while (!queue.isEmpty()) {
      var candidateCommand = queue.poll();
      if (candidateCommand.command.isReady()) {
        queue.addAll(commandsToKeep);
        return Optional.of(candidateCommand);
      }
      commandsToKeep.add(candidateCommand);
    }
    queue.addAll(commandsToKeep);
    return Optional.empty();
  }

  private Command tidyUp(QueuedCommand nextCommand) {
    cleanUpExpiredCommands(nextCommand);
    return batchAutomaticAnalyses(nextCommand.command);
  }

  private void cleanUpExpiredCommands(QueuedCommand nextQueuedCommand) {
    removeAll(queuedCommand -> !queuedCommand.command.isReady() && queuedCommand.getQueuedTime().plus(analysisExpirationDelay).isBefore(Instant.now()));
    if (nextQueuedCommand.command instanceof UnregisterModuleCommand unregisterCommand) {
      removeAll(queuedCommand -> (queuedCommand.command instanceof AnalyzeCommand analyzeCommand && analyzeCommand.getModuleKey().equals(unregisterCommand.getModuleKey()))
        || queuedCommand.command instanceof NotifyModuleEventCommand);
    }
  }

  private Command batchAutomaticAnalyses(Command nextCommand) {
    if (nextCommand instanceof AnalyzeCommand analyzeCommand && analyzeCommand.getTriggerType().canBeBatchedWithSameTriggerType()) {
      var removedCommands = (List<AnalyzeCommand>) removeAll(otherQueuedCommand -> canBeBatched(analyzeCommand, otherQueuedCommand.command));
      return Stream.concat(Stream.of(analyzeCommand), removedCommands.stream())
        .sorted((c1, c2) -> (int) (c1.getSequenceNumber() - c2.getSequenceNumber()))
        .reduce(AnalyzeCommand::mergeWith)
        // this last clause should never occur
        .orElse(analyzeCommand);
    }
    return nextCommand;
  }

  private static boolean canBeBatched(AnalyzeCommand analyzeCommand, Command otherCommand) {
    return otherCommand instanceof AnalyzeCommand otherAnalyzeCommand && otherAnalyzeCommand.getModuleKey().equals(analyzeCommand.getModuleKey())
      && otherAnalyzeCommand.getTriggerType().canBeBatchedWithSameTriggerType();
  }

  private List<? extends Command> removeAll(Predicate<QueuedCommand> predicate) {
    var iterator = queue.iterator();
    var removedCommands = new ArrayList<Command>();
    while (iterator.hasNext()) {
      var queuedCommand = iterator.next();
      if (predicate.test(queuedCommand)) {
        iterator.remove();
        queuedCommand.command.cancel();
        removedCommands.add(queuedCommand.command);
      }
    }
    return removedCommands;
  }

  private static class QueuedCommand {
    private final Command command;
    private final Instant queuedTime = Instant.now();

    QueuedCommand(Command command) {
      this.command = command;
    }

    public Command getCommand() {
      return command;
    }

    public Instant getQueuedTime() {
      return queuedTime;
    }
  }

  private static class CommandComparator implements Comparator<QueuedCommand> {
    private static final Map<Class<?>, Integer> COMMAND_TYPES_ORDERED = Map.ofEntries(
      // registering and unregistering modules have the highest priority
      // even if inserted later, they should be pulled first from the queue, before file events and analyzes: they might make them irrelevant
      // they both have the same priority so insertion order is respected
      entry(RegisterModuleCommand.class, 0), entry(UnregisterModuleCommand.class, 0),
      // forwarding file events takes priority over analyses, to make sure they give more accurate results
      entry(NotifyModuleEventCommand.class, 1),
      // analyses have the lowest priority
      entry(AnalyzeCommand.class, 2));

    @Override
    public int compare(QueuedCommand queuedCommand, QueuedCommand otherQueuedCommand) {
      var command = queuedCommand.command;
      var otherCommand = otherQueuedCommand.command;
      var commandRank = COMMAND_TYPES_ORDERED.get(command.getClass());
      var otherCommandRank = COMMAND_TYPES_ORDERED.get(otherCommand.getClass());
      return !Objects.equals(commandRank, otherCommandRank) ? (commandRank - otherCommandRank) :
        // for same command types, respect insertion order
        (int) (command.getSequenceNumber() - otherCommand.getSequenceNumber());
    }
  }

  private static Duration getAnalysisExpirationDelay() {
    try {
      var analysisExpirationDelayFromSystemProperty = System.getProperty(ANALYSIS_EXPIRATION_DELAY_PROPERTY_NAME);
      var parsedDelay = Duration.parse(analysisExpirationDelayFromSystemProperty);
      SonarLintLogger.get().debug("Overriding analysis expiration delay with value from system property: {}", parsedDelay);
      return parsedDelay;
    } catch (RuntimeException e) {
      SonarLintLogger.get().debug("Using default analysis expiration delay: {}", ANALYSIS_EXPIRATION_DEFAULT_DELAY);
      return ANALYSIS_EXPIRATION_DEFAULT_DELAY;
    }
  }

}

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.Command;
import org.sonarsource.sonarlint.core.analysis.command.NotifyModuleEventCommand;
import org.sonarsource.sonarlint.core.analysis.command.RegisterModuleCommand;
import org.sonarsource.sonarlint.core.analysis.command.UnregisterModuleCommand;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static java.util.Map.entry;

public class AnalysisQueue {
  private final PriorityQueue<Command> queue = new PriorityQueue<>(new CommandComparator());

  public synchronized void post(Command command) {
    queue.add(command);
    notifyAll();
  }

  public synchronized void wakeUp() {
    notifyAll();
  }

  public synchronized List<Command> removeAll() {
    var pendingTasks = new ArrayList<>(queue);
    queue.clear();
    return pendingTasks;
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
    removeAll(command -> !(command instanceof AnalyzeCommand));
  }

  private Optional<Command> pollNextReadyCommand() {
    var commandsToKeep = new ArrayList<Command>();
    // cannot use iterator as priority order is not guaranteed
    while (!queue.isEmpty()) {
      var candidateCommand = queue.poll();
      if (candidateCommand.isReady()) {
        queue.addAll(commandsToKeep);
        return Optional.of(candidateCommand);
      }
      commandsToKeep.add(candidateCommand);
    }
    queue.addAll(commandsToKeep);
    return Optional.empty();
  }

  private Command tidyUp(Command nextCommand) {
    cleanUpOutdatedCommands(nextCommand);
    return batchAutomaticAnalyses(nextCommand);
  }

  private void cleanUpOutdatedCommands(Command nextCommand) {
    if (nextCommand instanceof UnregisterModuleCommand unregisterCommand) {
      removeAll(command -> (command instanceof AnalyzeCommand analyzeCommand && analyzeCommand.getModuleKey().equals(unregisterCommand.getModuleKey()))
        || command instanceof NotifyModuleEventCommand);
    }
  }

  private Command batchAutomaticAnalyses(Command nextCommand) {
    if (nextCommand instanceof AnalyzeCommand analyzeCommand && analyzeCommand.getTriggerType().canBeBatchedWithSameTriggerType()) {
      var removedCommands = (List<AnalyzeCommand>) removeAll(otherCommand -> canBeBatched(analyzeCommand, otherCommand));
      removedCommands.forEach(AnalyzeCommand::cancel);
      SonarLintLogger.get().debug("Merging analysis command with " + removedCommands.size() + " commands");
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

  private List<? extends Command> removeAll(Predicate<Command> predicate) {
    var iterator = queue.iterator();
    var removedCommands = new ArrayList<Command>();
    while (iterator.hasNext()) {
      var command = iterator.next();
      if (predicate.test(command)) {
        iterator.remove();
        removedCommands.add(command);
      }
    }
    return removedCommands;
  }

  private static class CommandComparator implements Comparator<Command> {
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
    public int compare(Command command, Command otherCommand) {
      var commandRank = COMMAND_TYPES_ORDERED.get(command.getClass());
      var otherCommandRank = COMMAND_TYPES_ORDERED.get(otherCommand.getClass());
      return !Objects.equals(commandRank, otherCommandRank) ? (commandRank - otherCommandRank) :
      // for same command types, respect insertion order
        (int) (command.getSequenceNumber() - otherCommand.getSequenceNumber());
    }
  }
}

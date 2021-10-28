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
package org.sonarsource.sonarlint.core.container.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class RulesStore {

  private static final Logger LOG = Loggers.get(RulesStore.class);
  public static final String RULES_PB = "rules.pb";

  private final StorageFolder storageFolder;
  private final RWLock rwLock = new RWLock();

  public RulesStore(StorageFolder storageFolder) {
    this.storageFolder = storageFolder;
  }

  public void store(Sonarlint.Rules rules) {
    rwLock.write(() -> storageFolder.writeAction(dest -> ProtobufUtil.writeToFile(rules, dest.resolve(RULES_PB))));
  }

  public Sonarlint.Rules getAll() {
    return rwLock.read(() -> storageFolder.readAction(source -> {
      Path rulesPath = source.resolve(RULES_PB);
      if (Files.exists(rulesPath)) {
        return ProtobufUtil.readFile(rulesPath, Sonarlint.Rules.parser());
      } else {
        LOG.info("Unable to find rules in the SonarLint storage. You should update the storage.");
        return Sonarlint.Rules.newBuilder().build();
      }
    }));
  }

  public Sonarlint.Rules getAllOrEmpty() {
    try {
      return getAll();
    } catch (Exception e) {
      return Sonarlint.Rules.newBuilder().build();
    }
  }

  public Optional<Sonarlint.Rules.Rule> getRuleWithKey(String ruleKey) {
    return Optional.ofNullable(getAll().getRulesByKeyMap().get(ruleKey));
  }

}

/*
 * SonarLint Core - Medium Tests
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
package mediumtest.storage;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;

/**
 * Run in a separate process by {@link StorageConcurrentStartupMediumTests}, like the backend of another IDE window still opening the storage: takes the startup turn,
 * reports it, and keeps it until the stop file exists.
 * <p>
 * Arguments: the storage root, the stop file.
 */
public class HoldStartupTurnProcess {

  static final String HOLDING = "HOLDING";

  private HoldStartupTurnProcess() {
    // only used through main()
  }

  public static void main(String[] args) throws Exception {
    var h2Dir = Files.createDirectories(Path.of(args[0]).resolve("h2"));
    var stopFile = Path.of(args[1]);
    try (var channel = FileChannel.open(h2Dir.resolve(SonarLintDatabase.STARTUP_LOCK_FILENAME), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      var turn = channel.lock()) {
      System.out.println(HOLDING);
      System.out.flush();
      OpenStorageProcess.waitFor(stopFile);
    }
  }
}

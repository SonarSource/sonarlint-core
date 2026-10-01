/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.ai.ide;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.UninstallCliResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.UninstallCliResponse.Status;

/** Removes only the selected official installation; never recursively deletes user data. */
class SonarQubeCliUninstaller {
  private static final Duration RESET_TIMEOUT = Duration.ofSeconds(120);
  private static final String RESET_NOTICE = "Review reset stdout and stderr: executable removal does not confirm that every integration or credential was removed.";
  private final SonarQubeCliLocator locator;
  private final boolean windows;
  private final boolean mac;
  private final Path userHome;
  private final Map<String, String> environment;
  private final CliResetRunner runner;
  private final CliInstallerPathCleanup cleanup;
  private final FileRemoval removal;
  private final AtomicBoolean inProgress = new AtomicBoolean();
  private volatile boolean shutdownUncertain;

  SonarQubeCliUninstaller(SonarQubeCliLocator locator, Path userHome, Map<String, String> environment) {
    this(locator, userHome, environment, new CliResetRunner(), Files::delete);
  }

  SonarQubeCliUninstaller(SonarQubeCliLocator locator, Path userHome, Map<String, String> environment,
    CliResetRunner runner, FileRemoval removal) {
    this.locator = locator;
    this.windows = locator.isWindows();
    this.mac = locator.isMac();
    this.userHome = userHome;
    this.environment = Map.copyOf(environment);
    this.runner = runner;
    this.removal = removal;
    this.cleanup = new CliInstallerPathCleanup(windows, userHome, environment, runner);
  }

  boolean isAvailable(SonarQubeCliLocator.CliLookup cli) {
    return !shutdownUncertain && !inProgress.get() && eligible(cli).isPresent();
  }

  UninstallCliResponse uninstall(SonarLintCancelMonitor monitor) {
    if (!inProgress.compareAndSet(false, true)) {
      return response(Status.IN_PROGRESS, null, null, List.of("A CLI uninstall is already in progress."));
    }
    try {
      monitor.checkCanceled();
      if (shutdownUncertain) {
        return response(Status.NOT_AVAILABLE, null, null, List.of("Restart the backend before trying automatic uninstall again: previous cleanup shutdown is uncertain."));
      }
      Installation snapshot;
      try {
        snapshot = eligible(locator.find()).orElse(null);
      } catch (RuntimeException e) {
        monitor.checkCanceled();
        return response(Status.NOT_AVAILABLE, null, null, List.of("Could not safely resolve an official per-user CLI installation."));
      }
      if (snapshot == null) {
        return response(Status.NOT_AVAILABLE, null, null, List.of("Only the selected usable CLI in the official per-user installation location can be uninstalled automatically."));
      }
      var pathCleanup = cleanup.snapshot(snapshot.executable().getParent());
      var diagnostics = new ArrayList<>(pathCleanup.diagnostics());
      monitor.checkCanceled();
      if (!unchanged(snapshot)) {
        diagnostics.add("The CLI installation changed before reset. Refresh its state and try again.");
        return response(Status.NOT_AVAILABLE, snapshot.executable(), null, diagnostics);
      }
      var reset = runner.run(List.of(snapshot.executable().toString(), "system", "reset", "--force"), Map.of(), RESET_TIMEOUT, monitor);
      diagnostics.addAll(reset.diagnostics());
      shutdownUncertain |= !reset.shutdownConfirmed();
      monitor.checkCanceled();
      if (Integer.valueOf(0).equals(reset.exitCode())) {
        diagnostics.add(RESET_NOTICE);
      }
      if (reset.lifecycleFailed() || !Integer.valueOf(0).equals(reset.exitCode())) {
        diagnostics.add("Reset did not complete successfully. The executable and installer PATH configuration were retained.");
        return response(Status.RESET_FAILED, snapshot.executable(), reset, diagnostics);
      }
      monitor.checkCanceled();
      if (!unchanged(snapshot)) {
        diagnostics.add("The CLI executable disappeared or changed after reset. Its replacement and PATH configuration were retained.");
        return response(Status.EXECUTABLE_REMOVAL_FAILED, snapshot.executable(), reset, diagnostics);
      }
      monitor.checkCanceled();
      try {
        // No further cancellation checkpoints: once deletion starts, finish bounded installer cleanup.
        removal.delete(snapshot.executable());
      } catch (IOException | RuntimeException e) {
        diagnostics.add("Could not remove the CLI executable. Close processes using it and retry. Installer PATH configuration was retained.");
        return response(Status.EXECUTABLE_REMOVAL_FAILED, snapshot.executable(), reset, diagnostics);
      }
      boolean remainingConfiguration = !pathCleanup.diagnostics().isEmpty();
      var directoryDiagnostics = removeEmptyDirectories(snapshot);
      diagnostics.addAll(directoryDiagnostics);
      remainingConfiguration |= !directoryDiagnostics.isEmpty();
      var installerCleanup = cleanup.cleanup(pathCleanup);
      shutdownUncertain |= !installerCleanup.shutdownConfirmed();
      diagnostics.addAll(installerCleanup.diagnostics());
      remainingConfiguration |= !installerCleanup.diagnostics().isEmpty() || !installerCleanup.shutdownConfirmed();
      return response(remainingConfiguration ? Status.UNINSTALLED_WITH_REMAINING_CONFIGURATION : Status.UNINSTALLED,
        snapshot.executable(), reset, diagnostics);
    } finally {
      inProgress.set(false);
    }
  }

  private List<String> removeEmptyDirectories(Installation installation) {
    var diagnostics = new ArrayList<String>();
    var bin = installation.executable().getParent();
    for (var directory : List.of(bin, bin.getParent())) {
      try {
        var attributes = Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (Files.isSymbolicLink(directory) || !attributes.isDirectory()
          || !Identity.of(attributes).equals(installation.identities().get(directory))
          || !directory.toRealPath().equals(installation.canonicalAnchor().resolve(installation.anchor().relativize(directory)))) {
          diagnostics.add("Installer directory changed; remove it manually if appropriate: " + directory);
          continue;
        }
        removal.delete(directory);
      } catch (DirectoryNotEmptyException e) {
        diagnostics.add("Preserved nonempty installer directory: " + directory);
      } catch (IOException | RuntimeException e) {
        diagnostics.add("Could not remove empty installer directory: " + directory);
      }
    }
    return diagnostics;
  }

  private Optional<Installation> eligible(SonarQubeCliLocator.CliLookup cli) {
    if (cli.installationStatus() != CliInstallationStatus.INSTALLED || cli.path() == null) {
      return Optional.empty();
    }
    try {
      var anchor = installationAnchor();
      if (anchor == null || !anchor.isAbsolute()) {
        return Optional.empty();
      }
      anchor = anchor.normalize();
      var relative = windows ? Path.of("sonarqube-cli", "bin", "sonar.exe") : Path.of(".local", "share", "sonarqube-cli", "bin", "sonar");
      var expected = anchor.resolve(relative);
      if (!cli.path().isAbsolute() || !cli.path().normalize().equals(expected)) {
        return Optional.empty();
      }
      var canonicalAnchor = anchor.toRealPath();
      var identities = new LinkedHashMap<Path, Identity>();
      var current = anchor;
      for (var part : relative) {
        current = current.resolve(part);
        if (Files.isSymbolicLink(current) || !current.toRealPath().equals(canonicalAnchor.resolve(anchor.relativize(current)))) {
          return Optional.empty();
        }
        var attrs = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attrs.fileKey() == null || (current.equals(expected) ? !attrs.isRegularFile() : !attrs.isDirectory())) {
          return Optional.empty();
        }
        identities.put(current, Identity.of(attrs));
      }
      if (!windows && !Files.isExecutable(expected)) {
        return Optional.empty();
      }
      if (!hasNativeExecutableHeader(expected)) {
        return Optional.empty();
      }
      return Optional.of(new Installation(expected, anchor, canonicalAnchor, Map.copyOf(identities)));
    } catch (IOException | RuntimeException e) {
      return Optional.empty();
    }
  }

  private boolean hasNativeExecutableHeader(Path executable) throws IOException {
    try (var channel = FileChannel.open(executable, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
      var header = readHeader(channel, 0, 64);
      if (windows) {
        if (header.limit() < 64 || header.getShort(0) != (short) 0x4D5A) {
          return false;
        }
        var peOffset = Integer.toUnsignedLong(header.order(ByteOrder.LITTLE_ENDIAN).getInt(60));
        if (peOffset < 64 || channel.size() < peOffset + 24) {
          return false;
        }
        var signature = readHeader(channel, peOffset, 4);
        return signature.limit() == 4 && signature.getInt(0) == 0x50450000;
      }
      if (mac) {
        return isMachOHeader(header, channel.size());
      }
      return isElfExecutable(channel, header);
    }
  }

  private static boolean isElfExecutable(FileChannel channel, ByteBuffer header) throws IOException {
    // Official Linux CLI distributions are little-endian ELF64 for amd64 or arm64.
    if (header.limit() != 64 || header.getInt(0) != 0x7F454C46 || header.get(4) != 2 || header.get(5) != 1 || header.get(6) != 1) {
      return false;
    }
    header.order(ByteOrder.LITTLE_ENDIAN);
    var nativeMachine = switch (System.getProperty("os.arch", "")) {
      case "amd64", "x86_64" -> 62; // EM_X86_64
      case "aarch64", "arm64" -> 183; // EM_AARCH64
      default -> -1;
    };
    var executableType = Short.toUnsignedInt(header.getShort(16));
    if (nativeMachine < 0 || Short.toUnsignedInt(header.getShort(18)) != nativeMachine
      || (executableType != 2 && executableType != 3) || header.getInt(20) != 1 || header.getInt(48) != 0
      || Short.toUnsignedInt(header.getShort(52)) != 64 || Short.toUnsignedInt(header.getShort(54)) != 56) {
      return false;
    }
    var fileSize = channel.size();
    var programOffset = header.getLong(32);
    var programCount = Short.toUnsignedInt(header.getShort(56));
    if (programCount == 0 || programCount > 65536 / 56 || programOffset < 64
      || !withinFile(programOffset, (long) programCount * 56, fileSize)) {
      return false;
    }
    var entryPoint = header.getLong(24);
    boolean executableEntry = false;
    for (var index = 0; index < programCount; index++) {
      var program = readHeader(channel, programOffset + (long) index * 56, 56).order(ByteOrder.LITTLE_ENDIAN);
      if (program.limit() != 56 || !withinFile(program.getLong(8), program.getLong(32), fileSize)) {
        return false;
      }
      if (program.getInt(0) == 3 && !isElfInterpreter(channel, program)) { // PT_INTERP
        return false;
      }
      if (program.getInt(0) == 1) { // PT_LOAD
        if (!isElfLoadSegment(program)) {
          return false;
        }
        var virtualAddress = program.getLong(16);
        executableEntry |= (program.getInt(4) & 1) != 0 && entryPoint >= virtualAddress
          && entryPoint - virtualAddress < program.getLong(32);
      }
    }
    return executableEntry;
  }

  private static boolean withinFile(long offset, long size, long fileSize) {
    return offset >= 0 && size >= 0 && offset <= fileSize && size <= fileSize - offset;
  }

  private static boolean isElfLoadSegment(ByteBuffer program) {
    var offset = program.getLong(8);
    var address = program.getLong(16);
    var fileBytes = program.getLong(32);
    var memoryBytes = program.getLong(40);
    var alignment = program.getLong(48);
    return address >= 0 && memoryBytes >= fileBytes && memoryBytes <= Long.MAX_VALUE - address
      && (program.getInt(4) & ~7) == 0 && alignment >= 0 && (alignment <= 1 || (alignment & (alignment - 1)) == 0)
      && (alignment <= 1 || address % alignment == offset % alignment) && address % 4096 == offset % 4096;
  }

  private static boolean isElfInterpreter(FileChannel channel, ByteBuffer program) throws IOException {
    var size = program.getLong(32);
    if (size < 2 || size > 4096) {
      return false;
    }
    var interpreter = readHeader(channel, program.getLong(8), (int) size);
    if (interpreter.limit() != size || interpreter.get(0) != '/' || interpreter.get((int) size - 1) != 0) {
      return false;
    }
    for (var index = 0; index < size - 1; index++) {
      if (interpreter.get(index) == 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean isMachOHeader(ByteBuffer header, long fileSize) {
    if (header.limit() < 8) {
      return false;
    }
    var magic = header.getInt(0);
    return switch (magic) {
      case 0xFEEDFACE, 0xFEEDFACF, 0xCEFAEDFE, 0xCFFAEDFE -> {
        header.order(magic == 0xCEFAEDFE || magic == 0xCFFAEDFE ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        var headerSize = magic == 0xFEEDFACF || magic == 0xCFFAEDFE ? 32 : 28;
        yield header.limit() >= headerSize && header.getInt(12) == 2; // MH_EXECUTE
      }
      case 0xCAFEBABE, 0xCAFEBABF, 0xBEBAFECA, 0xBFBAFECA -> {
        header.order(magic == 0xBEBAFECA || magic == 0xBFBAFECA ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        var architectures = header.getInt(4);
        var entrySize = magic == 0xCAFEBABF || magic == 0xBFBAFECA ? 32 : 20;
        // Java class files share the fat magic; their version word is not an architecture count.
        yield architectures > 0 && architectures <= 16 && fileSize >= 8L + (long) architectures * entrySize;
      }
      default -> false;
    };
  }

  private static ByteBuffer readHeader(FileChannel channel, long offset, int length) throws IOException {
    var bytes = ByteBuffer.allocate(length);
    while (bytes.hasRemaining() && channel.read(bytes, offset + bytes.position()) > 0) {
      // Read only this fixed-size header, never the executable contents as a whole.
    }
    return bytes.flip();
  }

  @Nullable
  private Path installationAnchor() {
    if (!windows) {
      return userHome;
    }
    var localAppData = environment.entrySet().stream().filter(entry -> "LOCALAPPDATA".equalsIgnoreCase(entry.getKey()))
      .map(Map.Entry::getValue).findFirst().orElse(null);
    if (localAppData == null || localAppData.isBlank()) {
      return null;
    }
    try {
      return Path.of(localAppData);
    } catch (InvalidPathException e) {
      return null;
    }
  }

  private boolean unchanged(Installation installation) {
    try {
      var current = eligible(locator.find()).orElse(null);
      return current != null && current.executable().equals(installation.executable())
        && current.canonicalAnchor().equals(installation.canonicalAnchor()) && current.identities().equals(installation.identities());
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static UninstallCliResponse response(Status status, @Nullable Path executable, @Nullable CliResetRunner.Result reset,
    List<String> diagnostics) {
    return new UninstallCliResponse(status, executable == null ? null : executable.toString(), reset == null ? null : reset.exitCode(),
      reset == null ? "" : reset.stdout(), reset == null ? "" : reset.stderr(), diagnostics);
  }

  @FunctionalInterface
  interface FileRemoval {
    void delete(Path path) throws IOException;
  }

  private record Installation(Path executable, Path anchor, Path canonicalAnchor, Map<Path, Identity> identities) {
  }

  private record Identity(Object fileKey, long size, FileTime lastModified) {
    static Identity of(BasicFileAttributes attributes) {
      // Directory size/mtime can change as reset updates sibling files; only their identity matters.
      return new Identity(attributes.fileKey(), attributes.isDirectory() ? 0 : attributes.size(),
        attributes.isDirectory() ? FileTime.fromMillis(0) : attributes.lastModifiedTime());
    }
  }
}

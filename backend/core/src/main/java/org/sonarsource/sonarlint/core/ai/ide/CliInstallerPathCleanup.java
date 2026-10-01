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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;

/** Removes only PATH configuration owned by the official per-user installer. */
class CliInstallerPathCleanup {
  private static final int MAX_PROFILE_BYTES = 4 * 1024 * 1024;
  private static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(10);
  private static final String INSTALLER_MARKER = "# Added by sonarqube-cli installer";
  private static final String INSTALLER_EXPORT = "export PATH=\"$HOME/.local/share/sonarqube-cli/bin:$PATH\"";
  private static final String WINDOWS_BIN_ENV = "SONARQUBE_CLI_UNINSTALL_BIN";
  private static final String WINDOWS_CLEANUP_SCRIPT = """
    $ErrorActionPreference = 'Stop'
    $key = $null
    try {
      $directory = [IO.Path]::GetFullPath($env:SONARQUBE_CLI_UNINSTALL_BIN).TrimEnd([char[]]'\\/')
      $key = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey('Environment', $true)
      if ($null -eq $key -or $null -eq $key.GetValue('Path', $null, [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)) { exit 0 }
      $kind = $key.GetValueKind('Path')
      if ($kind -ne [Microsoft.Win32.RegistryValueKind]::String -and
          $kind -ne [Microsoft.Win32.RegistryValueKind]::ExpandString) {
        throw 'Unsupported user PATH registry value type.'
      }
      $original = $key.GetValue('Path', '', [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)
      $retained = [Collections.Generic.List[string]]::new()
      $removed = $false
      foreach ($entry in $original.Split([char[]]';', [StringSplitOptions]::None)) {
        $matches = $false
        try {
          $literal = $entry.Trim().Trim([char]'"')
          if (-not $literal.Contains('%') -and [IO.Path]::IsPathRooted($literal)) {
            $normalized = [IO.Path]::GetFullPath($literal).TrimEnd([char[]]'\\/')
            $matches = [String]::Equals($normalized, $directory, [StringComparison]::OrdinalIgnoreCase)
          }
        } catch {
          # An unrelated malformed entry must be preserved verbatim.
        }
        if ($matches) { $removed = $true } else { $retained.Add($entry) }
      }
      if ($removed) { $key.SetValue('Path', [String]::Join(';', $retained), $kind) }
    } catch {
      [Console]::Error.WriteLine('Could not update Windows user PATH; remove the SonarQube CLI installation directory manually.')
      exit 1
    } finally {
      if ($null -ne $key) { $key.Dispose() }
    }
    """;

  private final boolean windows;
  private final Path userHome;
  private final Map<String, String> environment;
  private final CliResetRunner runner;

  CliInstallerPathCleanup(boolean windows, Path userHome, Map<String, String> environment, CliResetRunner runner) {
    this.windows = windows;
    this.userHome = userHome.toAbsolutePath().normalize();
    this.environment = Map.copyOf(environment);
    this.runner = runner;
  }

  Snapshot snapshot(Path verifiedBinDirectory) {
    var profiles = new ArrayList<ProfileSnapshot>();
    var diagnostics = new ArrayList<String>();
    if (!windows) {
      for (var path : profileCandidates(diagnostics)) {
        inspectProfile(path, profiles, diagnostics);
      }
    }
    return new Snapshot(verifiedBinDirectory.toAbsolutePath().normalize(), List.copyOf(profiles), List.copyOf(new LinkedHashSet<>(diagnostics)));
  }

  CleanupResult cleanup(Snapshot snapshot) {
    if (windows) {
      return cleanupWindowsPath(snapshot.binDirectory());
    }
    var diagnostics = new ArrayList<String>();
    boolean shutdownConfirmed = true;
    for (var profile : snapshot.profiles()) {
      if (shutdownConfirmed) {
        shutdownConfirmed = cleanupProfile(profile, diagnostics);
      } else {
        diagnostics.add("Skipped shell profile " + profile.path() + " after uncertain helper shutdown; remove its SonarQube CLI PATH configuration manually.");
      }
    }
    return cleanupResult(diagnostics, shutdownConfirmed);
  }

  private LinkedHashSet<Path> profileCandidates(List<String> diagnostics) {
    var paths = new LinkedHashSet<Path>();
    for (var name : List.of(".profile", ".bashrc", ".bash_profile", ".zprofile", ".zshrc")) {
      paths.add(userHome.resolve(name));
    }
    var zshDirectory = environment.get("ZDOTDIR");
    if (zshDirectory != null && !zshDirectory.isBlank()) {
      addProfilePath(paths, zshDirectory, ".zprofile", "ZDOTDIR", diagnostics);
      addProfilePath(paths, zshDirectory, ".zshrc", "ZDOTDIR", diagnostics);
    }
    var override = environment.get("PROFILE");
    if (override != null && !override.isBlank() && !override.equals("/dev/null")) {
      addProfilePath(paths, override, "", "PROFILE", diagnostics);
    }
    return paths;
  }

  private static void addProfilePath(LinkedHashSet<Path> paths, String override, String filename, String variable, List<String> diagnostics) {
    try {
      var path = Path.of(override).resolve(filename);
      // Relative overrides depend on the installer's former working directory and cannot be recovered safely.
      if (path.isAbsolute()) {
        paths.add(path.normalize());
      } else {
        diagnostics.add("The " + variable + " override is relative; check its SonarQube CLI PATH configuration manually.");
      }
    } catch (InvalidPathException e) {
      diagnostics.add("The " + variable + " override is not a valid path; check its SonarQube CLI PATH configuration manually.");
    }
  }

  private static void inspectProfile(Path path, List<ProfileSnapshot> profiles, List<String> diagnostics) {
    try {
      if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        return;
      }
      var attributes = checkedAttributes(path);
      var metadata = readNativeMetadata(path);
      var original = readBoundedProfile(path);
      requireMatchingAttributes(attributes, checkedAttributes(path));
      requireMatchingMetadata(metadata, readNativeMetadata(path));
      var updated = removeInstallerBlocks(original);
      if (!Arrays.equals(original, updated)) {
        profiles.add(new ProfileSnapshot(path, attributes, metadata, original, updated));
      }
      if (containsCliPath(updated)) {
        diagnostics.add("Unrecognized SonarQube CLI PATH configuration remains in " + path + "; remove it manually.");
      }
    } catch (IOException | SecurityException | UnsupportedOperationException e) {
      diagnostics.add("Could not safely inspect shell profile " + path + "; check its SonarQube CLI PATH configuration manually.");
    }
  }

  private CleanupResult cleanupWindowsPath(Path binDirectory) {
    var powershell = windowsPowerShell();
    if (powershell == null) {
      return new CleanupResult(List.of("Could not locate the system PowerShell executable; remove the SonarQube CLI installation directory from Windows user PATH manually."), true);
    }
    var result = runner.run(List.of(powershell.toString(), "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", WINDOWS_CLEANUP_SCRIPT),
      Map.of(WINDOWS_BIN_ENV, binDirectory.toString()), CLEANUP_TIMEOUT, new SonarLintCancelMonitor());
    var diagnostics = new ArrayList<>(result.diagnostics());
    if (result.lifecycleFailed() || !result.shutdownConfirmed() || !Integer.valueOf(0).equals(result.exitCode())) {
      diagnostics.add("Could not update Windows user PATH; remove the SonarQube CLI installation directory manually.");
    }
    return cleanupResult(diagnostics, result.shutdownConfirmed());
  }

  private static CleanupResult cleanupResult(List<String> diagnostics, boolean shutdownConfirmed) {
    if (!shutdownConfirmed) {
      diagnostics.add("Installer cleanup shutdown could not be confirmed. Restart the backend before trying automatic uninstall again.");
    }
    return new CleanupResult(diagnostics, shutdownConfirmed);
  }

  @Nullable
  private Path windowsPowerShell() {
    var root = environment.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase("SystemRoot"))
      .map(Map.Entry::getValue).findFirst()
      .orElseGet(() -> environment.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase("WINDIR"))
        .map(Map.Entry::getValue).findFirst().orElse(null));
    if (root == null || root.isBlank()) {
      return null;
    }
    try {
      var directory = Path.of(root);
      if (!directory.isAbsolute()) {
        return null;
      }
      var executable = directory.resolve("System32/WindowsPowerShell/v1.0/powershell.exe").normalize();
      for (var ancestor = executable; ancestor != null; ancestor = ancestor.getParent()) {
        if (Files.isSymbolicLink(ancestor)) {
          return null;
        }
      }
      return Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS) ? executable : null;
    } catch (InvalidPathException | SecurityException e) {
      return null;
    }
  }

  private boolean cleanupProfile(ProfileSnapshot profile, List<String> diagnostics) {
    Path stagingDirectory = null;
    boolean shutdownConfirmed = true;
    try {
      requireUnchanged(profile);
      stagingDirectory = Files.createTempDirectory(profile.path().getParent(), ".sonarqube-cli-uninstall-");
      var stagedProfile = stagingDirectory.resolve("profile");
      var copy = runner.run(copyCommand(profile.path(), stagedProfile), Map.of(), CLEANUP_TIMEOUT, new SonarLintCancelMonitor());
      shutdownConfirmed = copy.shutdownConfirmed();
      if (copy.lifecycleFailed() || !copy.shutdownConfirmed() || !Integer.valueOf(0).equals(copy.exitCode())) {
        diagnostics.addAll(copy.diagnostics());
        throw new IOException("Native metadata-preserving profile copy failed");
      }
      checkedAttributes(stagedProfile);
      requireMatchingMetadata(profile.metadata(), readNativeMetadata(stagedProfile));
      if (!Arrays.equals(readBoundedProfile(stagedProfile), profile.original())) {
        throw new IOException("Profile changed while staging");
      }
      Files.write(stagedProfile, profile.updated());
      Files.getFileAttributeView(stagedProfile, BasicFileAttributeView.class)
        .setTimes(profile.attributes().lastModifiedTime(), profile.attributes().lastAccessTime(), null);
      requireMatchingMetadata(profile.metadata(), readNativeMetadata(stagedProfile));
      requireUnchanged(profile);
      Files.move(stagedProfile, profile.path(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      diagnostics.add("Shell profile " + profile.path() + " does not support atomic replacement; remove its SonarQube CLI PATH configuration manually.");
    } catch (IOException | SecurityException | UnsupportedOperationException e) {
      diagnostics.add("Could not safely update shell profile " + profile.path() + "; remove its SonarQube CLI PATH configuration manually.");
    } finally {
      removeStagingDirectory(stagingDirectory, diagnostics);
    }
    return shutdownConfirmed;
  }

  private static List<String> copyCommand(Path source, Path destination) {
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
      return List.of("/bin/cp", "-pP", "--preserve=all", source.toString(), destination.toString());
    }
    return List.of("/bin/cp", "-pP", source.toString(), destination.toString());
  }

  private static void removeStagingDirectory(Path stagingDirectory, List<String> diagnostics) {
    if (stagingDirectory == null) {
      return;
    }
    try {
      Files.deleteIfExists(stagingDirectory.resolve("profile"));
      Files.deleteIfExists(stagingDirectory);
    } catch (IOException | SecurityException | UnsupportedOperationException e) {
      diagnostics.add("Could not remove temporary shell-profile staging directory " + stagingDirectory + ".");
    }
  }

  private static void requireUnchanged(ProfileSnapshot profile) throws IOException {
    requireMatchingAttributes(profile.attributes(), checkedAttributes(profile.path()));
    if (!Arrays.equals(readBoundedProfile(profile.path()), profile.original())) {
      throw new IOException("Shell profile changed since inspection");
    }
    requireMatchingAttributes(profile.attributes(), checkedAttributes(profile.path()));
    requireMatchingMetadata(profile.metadata(), readNativeMetadata(profile.path()));
  }

  private static void requireMatchingAttributes(BasicFileAttributes expected, BasicFileAttributes actual) throws IOException {
    if (!Objects.equals(expected.fileKey(), actual.fileKey()) || expected.size() != actual.size()
      || !expected.lastModifiedTime().equals(actual.lastModifiedTime())) {
      throw new IOException("Shell profile changed since inspection");
    }
  }

  private static NativeMetadata readNativeMetadata(Path path) throws IOException {
    var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (posix == null) {
      throw new IOException("POSIX shell-profile metadata is unavailable");
    }
    var acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    return new NativeMetadata(posix.readAttributes(), acl == null ? null : List.copyOf(acl.getAcl()));
  }

  private static void requireMatchingMetadata(NativeMetadata expected, NativeMetadata actual) throws IOException {
    if (!Objects.equals(expected.acl(), actual.acl()) || !matchingPosixMetadata(expected.posix(), actual.posix())) {
      throw new IOException("Native shell-profile metadata could not be preserved");
    }
  }

  private static boolean matchingPosixMetadata(PosixFileAttributes expected, PosixFileAttributes actual) {
    return expected.owner().equals(actual.owner()) && expected.group().equals(actual.group()) && expected.permissions().equals(actual.permissions());
  }

  private static BasicFileAttributes checkedAttributes(Path path) throws IOException {
    for (var ancestor = path; ancestor != null; ancestor = ancestor.getParent()) {
      if (Files.isSymbolicLink(ancestor)) {
        throw new IOException("Symbolic shell-profile path");
      }
    }
    var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!attributes.isRegularFile() || attributes.fileKey() == null || attributes.size() > MAX_PROFILE_BYTES) {
      throw new IOException("Unsupported shell profile");
    }
    if (Files.getFileStore(path).supportsFileAttributeView("unix")) {
      if (((Number) Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue() != 1) {
        throw new IOException("Hard-linked shell profile");
      }
      if ((((Number) Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS)).intValue() & 07000) != 0) {
        throw new IOException("Special shell-profile permission bits");
      }
    }
    return attributes;
  }

  private static byte[] readBoundedProfile(Path path) throws IOException {
    try (var input = Files.newInputStream(path)) {
      var contents = input.readNBytes(MAX_PROFILE_BYTES + 1);
      if (contents.length > MAX_PROFILE_BYTES) {
        throw new IOException("Shell profile exceeds size limit");
      }
      return contents;
    }
  }

  private static byte[] removeInstallerBlocks(byte[] original) {
    var output = new ByteArrayOutputStream(original.length);
    var cursor = startsWithUtf8Bom(original) ? 3 : 0;
    output.write(original, 0, cursor);
    while (cursor < original.length) {
      var lineEnd = endOfLine(original, cursor);
      var nextLineEnd = endOfLine(original, lineEnd);
      if (lineEquals(original, cursor, lineEnd, INSTALLER_MARKER) && lineEquals(original, lineEnd, nextLineEnd, INSTALLER_EXPORT)) {
        cursor = nextLineEnd;
      } else {
        output.write(original, cursor, lineEnd - cursor);
        cursor = lineEnd;
      }
    }
    return output.toByteArray();
  }

  private static int endOfLine(byte[] bytes, int start) {
    for (var index = start; index < bytes.length; index++) {
      if (bytes[index] == '\n') {
        return index + 1;
      }
    }
    return bytes.length;
  }

  private static boolean lineEquals(byte[] bytes, int start, int end, String expected) {
    var contentEnd = end;
    if (contentEnd > start && bytes[contentEnd - 1] == '\n') {
      contentEnd--;
      if (contentEnd > start && bytes[contentEnd - 1] == '\r') {
        contentEnd--;
      }
    }
    return expected.equals(new String(bytes, start, contentEnd - start, StandardCharsets.ISO_8859_1));
  }

  private static boolean startsWithUtf8Bom(byte[] bytes) {
    return bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF;
  }

  private static boolean containsCliPath(byte[] bytes) {
    return new String(bytes, StandardCharsets.ISO_8859_1).contains("sonarqube-cli/bin");
  }

  record CleanupResult(List<String> diagnostics, boolean shutdownConfirmed) {
    CleanupResult {
      diagnostics = List.copyOf(diagnostics);
    }
  }

  record Snapshot(Path binDirectory, List<ProfileSnapshot> profiles, List<String> diagnostics) {
  }

  record ProfileSnapshot(Path path, BasicFileAttributes attributes, NativeMetadata metadata, byte[] original, byte[] updated) {
  }

  record NativeMetadata(PosixFileAttributes posix, @Nullable List<AclEntry> acl) {
  }
}

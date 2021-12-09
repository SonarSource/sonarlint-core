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
package org.sonarsource.sonarlint.core.plugin;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.plugin.SonarPluginManifest.RequiredPlugin;

import static java.util.Objects.requireNonNull;

public class PluginInfo implements Comparable<PluginInfo> {

  private static final Joiner SLASH_JOINER = Joiner.on(" / ").skipNulls();

  private final String key;
  private String name;

  @CheckForNull
  private File jarFile;

  @CheckForNull
  private String mainClass;

  @CheckForNull
  private Version version;

  @CheckForNull
  private Version minimalSqVersion;

  @CheckForNull
  private String basePlugin;

  private final Set<RequiredPlugin> requiredPlugins = new HashSet<>();

  @CheckForNull
  private Version jreMinVersion;

  @CheckForNull
  private Version nodeJsMinVersion;

  @CheckForNull
  private SkipReason skipReason;

  private boolean embedded;

  private List<String> dependencies = List.of();

  public PluginInfo(String key) {
    requireNonNull(key, "Plugin key is missing from manifest");
    this.key = key;
    this.name = key;
  }

  public PluginInfo setJarFile(File f) {
    this.jarFile = f;
    return this;
  }

  public File getJarFile() {
    return jarFile;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  @CheckForNull
  public Version getVersion() {
    return version;
  }

  @CheckForNull
  public Version getMinimalSqVersion() {
    return minimalSqVersion;
  }

  @CheckForNull
  public String getMainClass() {
    return mainClass;
  }

  @CheckForNull
  public String getBasePlugin() {
    return basePlugin;
  }

  public Set<RequiredPlugin> getRequiredPlugins() {
    return requiredPlugins;
  }

  @CheckForNull
  public Version getJreMinVersion() {
    return jreMinVersion;
  }

  @CheckForNull
  public Version getNodeJsMinVersion() {
    return nodeJsMinVersion;
  }

  public Optional<SkipReason> getSkipReason() {
    return Optional.ofNullable(skipReason);
  }

  public boolean isSkipped() {
    return skipReason != null;
  }

  public List<String> getDependencies() {
    return dependencies;
  }

  public PluginInfo setName(@Nullable String name) {
    this.name = MoreObjects.firstNonNull(name, this.key);
    return this;
  }

  public PluginInfo setVersion(Version version) {
    this.version = version;
    return this;
  }

  public PluginInfo setMinimalSqVersion(@Nullable Version v) {
    this.minimalSqVersion = v;
    return this;
  }

  /**
   * Required
   */
  public PluginInfo setMainClass(String mainClass) {
    this.mainClass = mainClass;
    return this;
  }

  public PluginInfo setBasePlugin(@Nullable String s) {
    if ("l10nen".equals(s)) {
      Loggers.get(PluginInfo.class).info("Plugin [{}] defines 'l10nen' as base plugin. " +
        "This metadata can be removed from manifest of l10n plugins since version 5.2.", key);
      basePlugin = null;
    } else {
      basePlugin = s;
    }
    return this;
  }

  public PluginInfo addRequiredPlugin(RequiredPlugin p) {
    this.requiredPlugins.add(p);
    return this;
  }

  private PluginInfo setMinimalJreVersion(@Nullable Version jreMinVersion) {
    this.jreMinVersion = jreMinVersion;
    return this;
  }

  private PluginInfo setMinimalNodeJsVersion(@Nullable Version nodeJsMinVersion) {
    this.nodeJsMinVersion = nodeJsMinVersion;
    return this;
  }

  public void setSkipReason(@Nullable SkipReason skipReason) {
    this.skipReason = skipReason;
  }

  public void setEmbedded(boolean embedded) {
    this.embedded = embedded;
  }

  public PluginInfo setDependencies(List<String> dependencies) {
    this.dependencies = dependencies;
    return this;
  }

  public boolean isEmbedded() {
    return embedded;
  }

  /**
   * Find out if this plugin is compatible with a given version of SonarQube.
   * The version of SQ must be greater than or equal to the minimal version
   * needed by the plugin.
   */
  public boolean isCompatibleWith(String implementedApi) {
    if (null == this.minimalSqVersion) {
      // no constraint defined on the plugin
      return true;
    }

    // Ignore patch and build numbers since this should not change API compatibility
    var requestedApi = Version.create(minimalSqVersion.getMajor() + "." + minimalSqVersion.getMinor());
    var implementedApiVersion = Version.create(implementedApi);
    return implementedApiVersion.compareToIgnoreQualifier(requestedApi) >= 0;
  }

  @Override
  public String toString() {
    return String.format("[%s]", SLASH_JOINER.join(key, version));
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PluginInfo info = (PluginInfo) o;
    if (!key.equals(info.key)) {
      return false;
    }
    return !(version != null ? !version.equals(info.version) : (info.version != null));

  }

  @Override
  public int hashCode() {
    int result = key.hashCode();
    result = 31 * result + (version != null ? version.hashCode() : 0);
    return result;
  }

  @Override
  public int compareTo(PluginInfo that) {
    return ComparisonChain.start()
      .compare(this.name, that.name)
      .compare(this.version, that.version, Ordering.natural().nullsFirst())
      .result();
  }

  public static PluginInfo create(Path jarFile, boolean isEmbedded) {
    var manifest = SonarPluginManifest.fromJar(jarFile);
    return create(jarFile, manifest, isEmbedded);
  }

  static PluginInfo create(Path jarPath, SonarPluginManifest manifest, boolean isEmbedded) {
    if (StringUtils.isBlank(manifest.getKey())) {
      throw MessageException.of(String.format("File is not a plugin. Please delete it and restart: %s", jarPath.toAbsolutePath()));
    }
    var info = new PluginInfo(manifest.getKey());

    info.setJarFile(jarPath.toFile());
    info.setName(manifest.getName());
    info.setMainClass(manifest.getMainClass());
    info.setVersion(Version.create(manifest.getVersion()));

    info.setMinimalSqVersion(manifest.getSonarMinVersion().orElse(null));
    info.setBasePlugin(manifest.getBasePluginKey());
    manifest.getRequiredPlugins().forEach(info::addRequiredPlugin);
    info.setMinimalJreVersion(manifest.getJreMinVersion().orElse(null));
    info.setMinimalNodeJsVersion(manifest.getNodeJsMinVersion().orElse(null));
    info.setDependencies(manifest.getDependencies());
    info.setEmbedded(isEmbedded);
    return info;
  }

}

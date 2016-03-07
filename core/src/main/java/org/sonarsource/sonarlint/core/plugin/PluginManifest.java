/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;

/**
 * This class loads Sonar plugin metadata from JAR manifest.
 */
public final class PluginManifest {

  public static final String KEY = "Plugin-Key";
  public static final String MAIN_CLASS = "Plugin-Class";
  public static final String NAME = "Plugin-Name";
  public static final String DESCRIPTION = "Plugin-Description";
  public static final String ORGANIZATION = "Plugin-Organization";
  public static final String ORGANIZATION_URL = "Plugin-OrganizationUrl";
  public static final String LICENSE = "Plugin-License";
  public static final String VERSION = "Plugin-Version";
  public static final String SONAR_VERSION = "Sonar-Version";
  public static final String DEPENDENCIES = "Plugin-Dependencies";
  public static final String HOMEPAGE = "Plugin-Homepage";
  public static final String TERMS_CONDITIONS_URL = "Plugin-TermsConditionsUrl";
  public static final String BUILD_DATE = "Plugin-BuildDate";
  public static final String ISSUE_TRACKER_URL = "Plugin-IssueTrackerUrl";
  public static final String REQUIRE_PLUGINS = "Plugin-RequirePlugins";

  /**
   * @since 0.3
   */
  public static final String USE_CHILD_FIRST_CLASSLOADER = "Plugin-ChildFirstClassLoader";

  /**
   * @since 1.1
   */
  public static final String BASE_PLUGIN = "Plugin-Base";

  /**
   * @since 1.3
   */
  public static final String IMPLEMENTATION_BUILD = "Implementation-Build";

  /**
   * @since 1.4
   */
  public static final String SOURCES_URL = "Plugin-SourcesUrl";

  /**
   * @since 1.4
   */
  public static final String DEVELOPERS = "Plugin-Developers";

  private String key;
  private String name;
  private String mainClass;
  private String description;
  private String organization;
  private String organizationUrl;
  private String license;
  private String version;
  private String sonarVersion;
  private String[] dependencies;
  private String homepage;
  private String termsConditionsUrl;
  private Date buildDate;
  private String issueTrackerUrl;
  private boolean useChildFirstClassLoader;
  private String basePlugin;
  private String implementationBuild;
  private String sourcesUrl;
  private String[] developers;
  private String[] requirePlugins;

  /**
   * Load the manifest from a JAR file.
   */
  public PluginManifest(File file) throws IOException {
    this();
    JarFile jar = null;
    try {
      jar = new JarFile(file);
      if (jar.getManifest() != null) {
        loadManifest(jar.getManifest());
      }
    } catch (Exception e) {
      throw new IllegalStateException("Unable to read plugin manifest from jar : " + file.getAbsolutePath(), e);
    } finally {
      if (jar != null) {
        jar.close();
      }
    }
  }

  /**
   * @param manifest can not be null
   */
  public PluginManifest(Manifest manifest) {
    this();
    loadManifest(manifest);
  }

  public PluginManifest() {
    dependencies = new String[0];
    developers = new String[0];
    useChildFirstClassLoader = false;
    requirePlugins = new String[0];
  }

  private void loadManifest(Manifest manifest) {
    Attributes attributes = manifest.getMainAttributes();
    this.key = PluginKeyUtils.sanitize(attributes.getValue(KEY));
    this.mainClass = attributes.getValue(MAIN_CLASS);
    this.name = attributes.getValue(NAME);
    this.description = attributes.getValue(DESCRIPTION);
    this.license = attributes.getValue(LICENSE);
    this.organization = attributes.getValue(ORGANIZATION);
    this.organizationUrl = attributes.getValue(ORGANIZATION_URL);
    this.version = attributes.getValue(VERSION);
    this.homepage = attributes.getValue(HOMEPAGE);
    this.termsConditionsUrl = attributes.getValue(TERMS_CONDITIONS_URL);
    this.sonarVersion = attributes.getValue(SONAR_VERSION);
    this.issueTrackerUrl = attributes.getValue(ISSUE_TRACKER_URL);
    this.buildDate = FormatUtils.toDate(attributes.getValue(BUILD_DATE), true);
    this.useChildFirstClassLoader = StringUtils.equalsIgnoreCase(attributes.getValue(USE_CHILD_FIRST_CLASSLOADER), "true");
    this.basePlugin = attributes.getValue(BASE_PLUGIN);
    this.implementationBuild = attributes.getValue(IMPLEMENTATION_BUILD);
    this.sourcesUrl = attributes.getValue(SOURCES_URL);

    String deps = attributes.getValue(DEPENDENCIES);
    this.dependencies = StringUtils.split(StringUtils.defaultString(deps), ' ');

    String devs = attributes.getValue(DEVELOPERS);
    this.developers = StringUtils.split(StringUtils.defaultString(devs), ',');

    String requires = attributes.getValue(REQUIRE_PLUGINS);
    this.requirePlugins = StringUtils.split(StringUtils.defaultString(requires), ',');
  }

  public String getKey() {
    return key;
  }

  public PluginManifest setKey(String key) {
    this.key = key;
    return this;
  }

  public String getName() {
    return name;
  }

  public PluginManifest setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * @since 3.5
   */
  public String[] getRequirePlugins() {
    return requirePlugins != null ? requirePlugins.clone() : null;
  }

  /**
   * @since 3.5
   */
  public PluginManifest setRequirePlugins(String[] requirePlugins) {
    this.requirePlugins = requirePlugins != null ? requirePlugins.clone() : null;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public PluginManifest setDescription(String description) {
    this.description = description;
    return this;
  }

  public String getOrganization() {
    return organization;
  }

  public PluginManifest setOrganization(String organization) {
    this.organization = organization;
    return this;
  }

  public String getOrganizationUrl() {
    return organizationUrl;
  }

  public PluginManifest setOrganizationUrl(String url) {
    this.organizationUrl = url;
    return this;
  }

  public String getLicense() {
    return license;
  }

  public PluginManifest setLicense(String license) {
    this.license = license;
    return this;
  }

  public String getVersion() {
    return version;
  }

  public PluginManifest setVersion(String version) {
    this.version = version;
    return this;
  }

  public String getSonarVersion() {
    return sonarVersion;
  }

  public PluginManifest setSonarVersion(String sonarVersion) {
    this.sonarVersion = sonarVersion;
    return this;
  }

  public String getMainClass() {
    return mainClass;
  }

  public PluginManifest setMainClass(String mainClass) {
    this.mainClass = mainClass;
    return this;
  }

  public String[] getDependencies() {
    return dependencies != null ? dependencies.clone() : null;
  }

  public PluginManifest setDependencies(String[] dependencies) {
    this.dependencies = dependencies != null ? dependencies.clone() : null;
    return this;
  }

  public Date getBuildDate() {
    return buildDate != null ? new Date(buildDate.getTime()) : null;
  }

  public PluginManifest setBuildDate(Date buildDate) {
    this.buildDate = buildDate != null ? new Date(buildDate.getTime()) : null;
    return this;
  }

  public String getHomepage() {
    return homepage;
  }

  public PluginManifest setHomepage(String homepage) {
    this.homepage = homepage;
    return this;
  }

  public String getTermsConditionsUrl() {
    return termsConditionsUrl;
  }

  public PluginManifest setTermsConditionsUrl(String termsConditionsUrl) {
    this.termsConditionsUrl = termsConditionsUrl;
    return this;
  }

  public String getIssueTrackerUrl() {
    return issueTrackerUrl;
  }

  public PluginManifest setIssueTrackerUrl(String issueTrackerUrl) {
    this.issueTrackerUrl = issueTrackerUrl;
    return this;
  }

  /**
   * @since 0.3
   */
  public boolean isUseChildFirstClassLoader() {
    return useChildFirstClassLoader;
  }

  /**
   * @since 0.3
   */
  public PluginManifest setUseChildFirstClassLoader(boolean useChildFirstClassLoader) {
    this.useChildFirstClassLoader = useChildFirstClassLoader;
    return this;
  }

  /**
   * @since 1.1
   */
  public String getBasePlugin() {
    return basePlugin;
  }

  /**
   * @since 1.1
   */
  public PluginManifest setBasePlugin(String key) {
    this.basePlugin = key;
    return this;
  }

  /**
   * @since 1.3
   */
  public String getImplementationBuild() {
    return implementationBuild;
  }

  /**
   * @since 1.3
   */
  public PluginManifest setImplementationBuild(String implementationBuild) {
    this.implementationBuild = implementationBuild;
    return this;
  }

  /**
   * @since 1.4
   */
  public String getSourcesUrl() {
    return sourcesUrl;
  }

  /**
   * @since 1.4
   */
  public PluginManifest setSourcesUrl(String sourcesUrl) {
    this.sourcesUrl = sourcesUrl;
    return this;
  }

  /**
   * @since 1.4
   */
  public String[] getDevelopers() {
    return developers;
  }

  /**
   * @since 1.4
   */
  public PluginManifest setDevelopers(String[] developers) {
    this.developers = developers;
    return this;
  }

  @Override
  public String toString() {
    return new ReflectionToStringBuilder(this).toString();
  }

  public boolean isValid() {
    return StringUtils.isNotBlank(key) && StringUtils.isNotBlank(version);
  }

}

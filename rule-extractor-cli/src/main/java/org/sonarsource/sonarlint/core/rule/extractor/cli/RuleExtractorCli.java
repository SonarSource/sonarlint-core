/*
 * SonarLint Core - Rule Extractor - CLI
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.rule.extractor.cli;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractor;
import picocli.CommandLine;

@CommandLine.Command(name = "sonarlint-rule-extractor-cli", mixinStandardHelpOptions = true)
public class RuleExtractorCli implements Callable<Integer> {

  @CommandLine.Option(names = {"-p", "--plugins"}, split = ",", description = "path to plugin(s) JARs")
  Set<Path> pluginsJarPaths = Set.of();

  @CommandLine.Option(names = {"-l", "--languages"}, split = ",", description = "enabled languages. Valid values: ${COMPLETION-CANDIDATES}")
  Set<Language> enabledLanguages = Set.of();

  @CommandLine.Option(names = "--include-templates", description = "include template rules")
  boolean templates;

  @CommandLine.Option(names = "--include-hotspots", description = "include hotspot rules")
  boolean hotspots;

  @CommandLine.Option(names = {"-o", "--output"}, description = "output JSON file name")
  Path outputFile;
  @Override
  public Integer call() throws Exception {
    try {
      // We can pretend we have a very high Node.js version since Node is not required to load rules
      var nodeVersion = Optional.of(Version.create("99.9"));
      var config = new PluginsLoader.Configuration(pluginsJarPaths, enabledLanguages, nodeVersion);
      var result = new PluginsLoader().load(config);
      for (var entry : result.getPluginCheckResultByKeys().entrySet()) {
        if (entry.getValue().isSkipped()) {
          System.err.println(entry.getKey() + " was skipped:" + entry.getValue().getSkipReason().get().getClass().getName());
        }
      }
      var extractor = new RulesDefinitionExtractor();
      var rules = extractor.extractRules(result.getLoadedPlugins().getPluginInstancesByKeys(), enabledLanguages, templates, hotspots);
      var gson = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Optional.class, new GsonOptionalDeserializer<Optional<?>>())
        .create();

      var jsonString = gson.toJson(rules);
      if (outputFile != null) {
        Files.write(outputFile, jsonString.getBytes(StandardCharsets.UTF_8));
      } else {
        System.out.println(jsonString);
      }
      return 0;
    } catch (Exception e) {
      e.printStackTrace(System.err);
      return -1;
    }
  }

  public static void main(String... args) {
    System.exit(execute(args));
  }

  public static int execute(String... args) {
    return new CommandLine(new RuleExtractorCli()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
  }

  public static class GsonOptionalDeserializer<T> implements JsonSerializer<Optional<T>> {
    @Override
    public JsonElement serialize(Optional<T> src, Type typeOfSrc, JsonSerializationContext context) {
      return context.serialize(src.orElse(null));
    }
  }
}

/*
 * Copyright (C) 2009-2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package org.sonar.samples.javascript;

import com.google.common.collect.ImmutableList;
import org.sonar.api.SonarPlugin;

import java.util.List;

/**
 * Extension point to define a Sonar Plugin.
 */
public class JavaScriptCustomRulesPlugin extends SonarPlugin {

  @Override
  public List getExtensions() {
    return ImmutableList.of(
      JavascriptCustomRulesDefinition.class
    );
  }

}

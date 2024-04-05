package org.sonar.plugins.dependent;

import java.util.Collections;
import java.util.List;
import org.sonar.api.Plugin;
import org.sonar.api.Plugin.Context;
import org.sonar.plugins.base.api.BaseApi;

public class DependentPlugin implements Plugin {
  public DependentPlugin() {
    // uses a class that is exported by base-plugin
    new BaseApi().doNothing();
  }

  @Override
  public void define(Context context) {
    // no extensions
  }
}

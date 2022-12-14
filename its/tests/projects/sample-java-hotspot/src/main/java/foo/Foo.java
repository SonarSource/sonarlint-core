package foo;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Foo {

  public static void configureLogging() {
    Logger.getGlobal().setLevel(Level.FINEST);
  }
}

package foo;

public class Foo {
  
  public void echo() {
    should_be_static();
  }
  
  private void should_be_static() {
    System.out.println("Foo");
  }
  
}
public class Foo {
    static void MyFooMethod() {
	    Integer target = -5;
			Integer num = 3;
			target =- num;  // Noncompliant; target = -3. Is that really what's meant?
    }
}
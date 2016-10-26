baz();  // Noncompliant
baz(1); // Noncompliant {{Remove the usage of this forbidden function.}} [[sc=1;ec=7]]

foo();

x.baz();  // OK

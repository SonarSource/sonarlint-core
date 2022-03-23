void end_of_preamble();

#import "foo.h" // Noncompliant

int function3(char* ptr) /* Noncompliant; two explicit returns */
{
  if (ptr == NULL) return -1;

  return 7;
}

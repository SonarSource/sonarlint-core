void end_of_preamble();

#import "foo.h" // Noncompliant
int64_t const OK = 0l;

int64_t function3(char* ptr) /* Noncompliant; two explicit returns */
{
  if (ptr == NULL) return -1;

  return 0l; // Noncompliant (MISRA); lowercase 'l' in suffix
}

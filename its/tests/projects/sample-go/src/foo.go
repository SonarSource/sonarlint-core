package samples

func noHardcodedCredentials() string  {
  password := "bar" // Noncompliant S2068
  return password
}

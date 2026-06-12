You verify one deprecation entry against real source code in `repo_paths`.

**Output YAML:**

```yaml
id: <deprecation id>
verdict: confirmed | false_positive | partial
evidence:
  - repo: <name>
    file: <path>
    lines: [<nums>]
    note: <why this supports or refutes the finding>
affected_repos: [<subset after partial verification>]
```

Read actual files. Do not assume headers, auth mechanisms, or API versions not evidenced in code.

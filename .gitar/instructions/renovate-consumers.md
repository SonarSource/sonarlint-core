# Downstream consumers in Renovate safety reviews

Only when performing an eligible Renovate dependency safety review under the inherited shared rule:

- Inspect at most two relevant accessible downstream consumers for compatibility impacts.
- For public PRs, use only public consumers. Never disclose private or inaccessible repository names or contents.
- Add relevant findings to the existing dependency safety advisory; do not start a separate review or post another advisory.

# Architecture rules

- `core/*` modules are plain Kotlin/JVM. Never add an Android dependency to them.
- `core/domain` depends on the Kotlin stdlib only. Adding a dependency needs an ADR.
- Every OS capability is a port in `org.organiza.domain.port`, with a fake in `core/testing`.
- Android adapters stay thin: translate framework calls to ports, and keep decisions in core.
- Expected failures are typed values (`Parsed.Invalid`, sealed types), not exceptions.

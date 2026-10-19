# Trivy Security Scan — obsrv-core

## Remediation status (2026-08-12) — COMPLETE

**117 → 0 findings.** Re-scan: CRITICAL 0, HIGH 0, MEDIUM 0, LOW 0, misconfigurations 0, secrets 0. Full `mvn clean install` test suite green.

Note on the pipeline test flakiness investigated along the way: the intermittent count-assertion failures (preprocessor/transformer/denormalizer, e.g. "7 was not equal to 1") were NOT caused by any dependency bump. Root cause was `BaseSpecWithDatasetRegistry.getMetrics` using `InMemoryReporter.findGroups(dataset)`, which substring-matches the dataset id against group identifiers containing random hex ids — short ids like "d3" are valid hex and matched foreign groups, inflating counts. Fixed by filtering on exact scope components; preprocessor went from 3/5 failing to 5/5 passing.

Key fixes applied: log4j-core 2.25.4 (Log4Shell), commons-text 1.10.0 (Text4Shell), log4j 1.x excluded (EOL, log4j-1.2-api bridge added), netty-bom 4.1.136.Final, zookeeper 3.9.5, kafka-clients 3.9.2, postgresql 42.7.12, commons-lang3 3.18.0, lz4-java 1.11.1, opentelemetry 1.62.0, jackson 2.18.8; Dockerfile HEALTHCHECKs + non-root USER + apt-get hygiene + build-secret handling in stubs.

---

## Original scan (baseline)

- **Scan date:** 2026-08-11
- **Branch/commit:** `main` @ `2c429bb` (v2.2.0) — fixes on `trivy-vuln-fixes`
- **Command:** `trivy fs --scanners vuln,misconfig,secret` (offline-scan mode)
- **Totals:** LOW: 4 · MEDIUM: 56 · HIGH: 49 · CRITICAL: 8 · **TOTAL: 117** (Secrets: 0)

## LOW

### Vulnerabilities

| ID | Package | Installed | Fixed | Target | Title |
|---|---|---|---|---|---|
| CVE-2025-58056 | io.netty:netty-codec-http | 4.1.63.Final | 4.1.125.Final, 4.2.5.Final | data-products/pom.xml | netty-codec-http: Netty is vulnerable to request smuggling due to incorrect parsing of chu |

### Misconfigurations

| Rule | File | Issue | Resolution |
|---|---|---|---|
| DS-0026 | Dockerfile | No HEALTHCHECK defined | Add HEALTHCHECK instruction in Dockerfile |
| DS-0026 | stubs/docker/apache-flink-plugins/Dockerfile | No HEALTHCHECK defined | Add HEALTHCHECK instruction in Dockerfile |
| DS-0026 | stubs/docker/apache-flink/Dockerfile | No HEALTHCHECK defined | Add HEALTHCHECK instruction in Dockerfile |

## MEDIUM

### Vulnerabilities

| ID | Package | Installed | Fixed | Target | Title |
|---|---|---|---|---|---|
| CVE-2026-59949 | at.yawk.lz4:lz4-java | 1.10.3 | 1.11.1 | framework/pom.xml<br>pom.xml | LZ4 Java: Native XXHash implementations can crash the JVM when passed invalid byte array r |
| GHSA-72hv-8253-57qq | com.fasterxml.jackson.core:jackson-core | 2.17.2 | 2.21.1, 2.18.6 | data-products/pom.xml<br>framework/pom.xml<br>pom.xml | jackson-core: Number Length Constraint Bypass in Async Parser Leads to Potential DoS Condi |
| CVE-2026-54514 | com.fasterxml.jackson.core:jackson-databind | 2.17.2 | 2.18.8, 2.21.4, 3.1.4 | data-products/pom.xml<br>framework/pom.xml<br>pom.xml | jackson-databind: jackson-databind: Information Disclosure via Eager DNS Resolution |
| CVE-2026-54515 | com.fasterxml.jackson.core:jackson-databind | 2.17.2 | 3.1.4, 2.18.9, 2.21.5, 2.22.1 | data-products/pom.xml<br>framework/pom.xml<br>pom.xml | jackson-databind: jackson-databind: Ignored properties can be unexpectedly modified |
| CVE-2026-59888 | com.fasterxml.jackson.core:jackson-databind | 2.17.2 | 2.18.8, 2.21.4 | data-products/pom.xml<br>framework/pom.xml<br>pom.xml | com.fasterxml.jackson.core/jackson-databind: tools.jackson.core/jackson-databind: jackson- |
| CVE-2025-58057 | io.netty:netty-codec | 4.1.63.Final | 4.1.125.Final | data-products/pom.xml | netty-codec: netty-codec-compression: Netty's BrotliDecoder is vulnerable to DoS via zip b |
| CVE-2021-43797 | io.netty:netty-codec-http | 4.1.63.Final | 4.1.71.Final | data-products/pom.xml | netty: control chars in header names may lead to HTTP request smuggling |
| CVE-2022-24823 | io.netty:netty-codec-http | 4.1.63.Final | 4.1.77.Final | data-products/pom.xml | netty: world readable temporary file containing sensitive data |
| CVE-2024-29025 | io.netty:netty-codec-http | 4.1.63.Final | 4.1.108.Final | data-products/pom.xml | netty-codec-http: Allocation of Resources Without Limits or Throttling |
| CVE-2025-67735 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.8.Final, 4.1.129.Final | data-products/pom.xml | netty-codec-http: Netty (netty-codec-http): Request Smuggling via CRLF Injection |
| CVE-2026-41417 | io.netty:netty-codec-http | 4.1.63.Final | 4.1.133.Final, 4.2.13.Final | data-products/pom.xml | netty: Netty: HTTP request smuggling via URI manipulation and CRLF injection |
| CVE-2026-42580 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.13.Final, 4.1.133.Final | data-products/pom.xml | netty: Netty: Request smuggling via chunk size parser integer overflow |
| CVE-2026-42581 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.13.Final, 4.1.133.Final | data-products/pom.xml | netty: io.netty/netty-codec-http: Netty: HTTP Request Smuggling due to improper handling o |
| CVE-2026-42585 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.13.Final, 4.1.133.Final | data-products/pom.xml | netty: io.netty/netty-codec-http: Netty: Request smuggling via malformed Transfer-Encoding |
| CVE-2026-50020 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.15.Final, 4.1.135.Final | data-products/pom.xml | netty-codec-http: Netty: Data manipulation via request-boundary confusion in HttpObjectDec |
| CVE-2026-56746 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.16.Final, 4.1.136.Final | data-products/pom.xml | io.netty/netty-codec-http: Netty: Security control bypass allows unauthorized requests via |
| CVE-2026-59898 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.16.Final, 4.1.136.Final | data-products/pom.xml | io.netty/netty-codec-http: Netty: Protocol version confusion in netty-codec-http (WebSocke |
| CVE-2026-59899 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.16.Final, 4.1.136.Final | data-products/pom.xml | io.netty/netty-codec-http: Netty: Memory exhaustion in netty-codec-http (decompression bom |
| CVE-2026-59921 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.16.Final, 4.1.136.Final | data-products/pom.xml | io.netty/netty-codec-http: Netty: CRLF Injection via Multipart Filename in Netty HttpPostR |
| CVE-2026-47244 | io.netty:netty-codec-http2 | 4.1.63.Final | 4.2.15.Final, 4.1.135.Final | data-products/pom.xml | netty-codec-http2: Netty: Denial of Service via uncontrolled HTTP/2 concurrent streams |
| CVE-2026-48043 | io.netty:netty-codec-http2 | 4.1.63.Final | 4.1.135.Final, 4.2.15.Final | data-products/pom.xml | netty-codec-http2: netty-codec-http2: Denial of Service due to resource leak |
| CVE-2026-50560 | io.netty:netty-codec-http2 | 4.1.63.Final | 4.2.15.Final, 4.1.135.Final | data-products/pom.xml | netty-codec-http2: Netty: Denial of Service due to HTTP/2 max header size handling |
| CVE-2026-59900 | io.netty:netty-codec-http2 | 4.1.63.Final | 4.2.16.Final, 4.1.136.Final | data-products/pom.xml | io.netty/netty-codec-http2: Netty: Improper header neutralization in netty-codec-http2 |
| CVE-2024-47535 | io.netty:netty-common | 4.1.63.Final | 4.1.115.Final | data-products/pom.xml | netty: Denial of Service attack on windows app using Netty |
| CVE-2025-25193 | io.netty:netty-common | 4.1.63.Final | 4.1.118.Final | data-products/pom.xml | netty: Denial of Service attack on windows app using Netty |
| CVE-2023-34462 | io.netty:netty-handler | 4.1.50.Final | 4.1.94.Final | data-products/pom.xml | netty: SniHandler 16MB allocation leads to OOM |
| CVE-2026-45536 | io.netty:netty-transport-native-epoll | 4.1.50.Final | 4.2.15.Final, 4.1.135.Final | data-products/pom.xml | netty-transport-native-epoll: netty-transport-native-kqueue: Netty: Denial of Service due  |
| CVE-2026-45292 | io.opentelemetry:opentelemetry-api | 1.42.1 | 1.62.0 | framework/pom.xml<br>pom.xml | opentelemetry-java: opentelemetry-api: opentelemetry-extension-trace-propagators: OpenTele |
| CVE-2025-48924 | org.apache.commons:commons-lang3 | 3.8.1 | 3.18.0 | data-products/pom.xml | commons-lang/commons-lang: org.apache.commons/commons-lang3: Uncontrolled Recursion vulner |
| CVE-2025-48924 | org.apache.commons:commons-lang3 | 3.9 | 3.18.0 | pipeline/pom.xml<br>pipeline/preprocessor/pom.xml<br>pom.xml | commons-lang/commons-lang: org.apache.commons/commons-lang3: Uncontrolled Recursion vulner |
| CVE-2026-33558 | org.apache.kafka:kafka-clients | 3.9.1 | 3.9.2, 4.0.1 | framework/pom.xml<br>pipeline/cache-indexer/pom.xml<br>pipeline/pom.xml<br>pipeline/unified-pipeline/pom.xml<br>pom.xml | Apache Kafka exposes sensitive information in its DEBUG logs |
| CVE-2021-44832 | org.apache.logging.log4j:log4j-core | 2.14.1 | 2.3.2, 2.12.4, 2.17.1 | data-products/pom.xml | log4j-core: remote code execution via JDBC Appender |
| CVE-2025-68161 | org.apache.logging.log4j:log4j-core | 2.14.1 | 2.25.3 | data-products/pom.xml | Apache Log4j: Apache Log4j Core: Information disclosure via missing TLS hostname verificat |
| CVE-2026-34477 | org.apache.logging.log4j:log4j-core | 2.14.1 | 2.25.4 | data-products/pom.xml | org.apache.logging.log4j/log4j-core: Apache Log4j Core: Man-in-the-middle attack due to in |
| CVE-2026-34480 | org.apache.logging.log4j:log4j-core | 2.14.1 | 2.25.4 | data-products/pom.xml | org.apache.logging.log4j/log4j-core: Apache Log4j Core: Invalid XML output causes denial o |

### Misconfigurations

| Rule | File | Issue | Resolution |
|---|---|---|---|

## HIGH

### Vulnerabilities

| ID | Package | Installed | Fixed | Target | Title |
|---|---|---|---|---|---|
| GHSA-r7wm-3cxj-wff9 | com.fasterxml.jackson.core:jackson-core | 2.17.2 | 2.18.8, 2.21.4 | data-products/pom.xml<br>framework/pom.xml<br>pom.xml | jackson-core: Async parser maxNumberLength bypass via chunked digit accumulation (incomple |
| CVE-2026-54512 | com.fasterxml.jackson.core:jackson-databind | 2.17.2 | 2.18.8, 3.1.4, 2.21.4 | data-products/pom.xml<br>framework/pom.xml<br>pom.xml | jackson-databind: jackson-databind: Arbitrary code execution via PolymorphicTypeValidator  |
| CVE-2026-54513 | com.fasterxml.jackson.core:jackson-databind | 2.17.2 | 2.18.8, 2.21.4, 3.1.4 | data-products/pom.xml<br>framework/pom.xml<br>pom.xml | jackson-databind: Jackson-databind: Security bypass allows arbitrary code execution |
| CVE-2021-37136 | io.netty:netty-codec | 4.1.63.Final | 4.1.68.Final | data-products/pom.xml | netty-codec: Bzip2Decoder doesn't allow setting size restrictions for decompressed data |
| CVE-2021-37137 | io.netty:netty-codec | 4.1.63.Final | 4.1.68.Final | data-products/pom.xml | netty-codec: SnappyFrameDecoder doesn't restrict chunk length and may buffer skippable chu |
| CVE-2026-42583 | io.netty:netty-codec | 4.1.63.Final | 4.1.133.Final | data-products/pom.xml | netty: io.netty/netty-codec-compression: io.netty/netty-codec: Netty: Denial of Service vi |
| CVE-2026-59901 | io.netty:netty-codec | 4.1.63.Final | 4.1.136.Final | data-products/pom.xml | io.netty/netty-codec-compression: Netty: Infinite loop in netty-codec-compression (bzip2) |
| CVE-2026-33870 | io.netty:netty-codec-http | 4.1.63.Final | 4.1.132.Final, 4.2.10.Final | data-products/pom.xml | io.netty/netty-codec-http: Netty: Request smuggling via incorrect parsing of HTTP/1.1 chun |
| CVE-2026-42584 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.13.Final, 4.1.133.Final | data-products/pom.xml | netty: io.netty/netty-codec-http: Netty: Incorrect HTTP response parsing leads to data con |
| CVE-2026-42587 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.13.Final, 4.1.133.Final | data-products/pom.xml | netty: io.netty/netty-codec-http: io.netty/netty-codec-http2: Netty: Denial of Service via |
| CVE-2026-55831 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.16.Final, 4.1.136.Final | data-products/pom.xml | io.netty/netty-codec-http: Netty: Denial of Service via SPDY SETTINGS frame processing |
| CVE-2026-55833 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.16.Final, 4.1.136.Final | data-products/pom.xml | netty: io.netty/netty-codec-http: Netty: Denial of Service via SPDY header decompression a |
| CVE-2026-56745 | io.netty:netty-codec-http | 4.1.63.Final | 4.2.16.Final, 4.1.136.Final | data-products/pom.xml | netty: io.netty/netty-codec-http: Netty: Denial of Service via memory exhaustion in SPDY-t |
| CVE-2025-55163 | io.netty:netty-codec-http2 | 4.1.63.Final | 4.2.4.Final, 4.1.124.Final | data-products/pom.xml | netty: netty-codec-http2: Netty MadeYouReset HTTP/2 DDoS Vulnerability |
| CVE-2026-33871 | io.netty:netty-codec-http2 | 4.1.63.Final | 4.1.132.Final, 4.2.11.Final | data-products/pom.xml | netty: Netty: Denial of Service via HTTP/2 CONTINUATION frame flood |
| CVE-2026-42587 | io.netty:netty-codec-http2 | 4.1.63.Final | 4.2.13.Final, 4.1.133.Final | data-products/pom.xml | netty: io.netty/netty-codec-http: io.netty/netty-codec-http2: Netty: Denial of Service via |
| CVE-2026-56819 | io.netty:netty-codec-http2 | 4.1.63.Final | 4.2.16.Final, 4.1.136.Final | data-products/pom.xml | io.netty/netty-codec-http2: Netty: Denial of Service via HTTP/2 DATA frame memory leak |
| GHSA-xpw8-rcwv-8f8p | io.netty:netty-codec-http2 | 4.1.63.Final | 4.1.100.Final | data-products/pom.xml | io.netty:netty-codec-http2 vulnerable to HTTP/2 Rapid Reset Attack |
| CVE-2026-44249 | io.netty:netty-handler | 4.1.50.Final | 4.2.15.Final, 4.1.135.Final | data-products/pom.xml | netty-handler: netty-handler: IPv6 subnet rule bypass due to incorrect masking operation |
| CVE-2026-45416 | io.netty:netty-handler | 4.1.50.Final | 4.2.15.Final, 4.1.135.Final | data-products/pom.xml | netty-handler: Netty: Denial of Service due to eager buffer allocation in TLS handshake |
| CVE-2026-50010 | io.netty:netty-handler | 4.1.50.Final | 4.2.15.Final, 4.1.135.Final | data-products/pom.xml | netty-handler: Netty: Improper trust manager handling leads to hostname verification bypas |
| CVE-2021-4104 | log4j:log4j | 1.2.17 | **no fix** | data-products/pom.xml | log4j: Remote code execution in Log4j 1.x when application is configured to use JMSAppende |
| CVE-2022-23302 | log4j:log4j | 1.2.17 | **no fix** | data-products/pom.xml | log4j: Remote code execution in Log4j 1.x when application is configured to use JMSSink |
| CVE-2023-26464 | log4j:log4j | 1.2.17 | 2.0 | data-products/pom.xml | log4j1-socketappender: DoS via hashmap logging |
| CVE-2026-35554 | org.apache.kafka:kafka-clients | 3.9.1 | 3.9.2, 4.0.2, 4.1.2 | framework/pom.xml<br>pipeline/cache-indexer/pom.xml<br>pipeline/pom.xml<br>pipeline/unified-pipeline/pom.xml<br>pom.xml | Apache Kafka Clients: Apache Kafka Clients: Information disclosure and data corruption due |
| CVE-2021-45105 | org.apache.logging.log4j:log4j-core | 2.14.1 | 2.12.3, 2.17.0, 2.3.1 | data-products/pom.xml | log4j-core: DoS in log4j 2.x with Thread Context Map (MDC) input data contains a recursive |
| CVE-2026-42198 | org.postgresql:postgresql | 42.7.7 | 42.7.11 | framework/pom.xml<br>pom.xml | jdbc.postgresql.org: pgjdbc: Client-side Denial of Service via malicious SCRAM-SHA-256 aut |
| CVE-2026-54291 | org.postgresql:postgresql | 42.7.7 | 42.7.12 | framework/pom.xml<br>pom.xml | org.postgresql/postgresql: com.ongres.scram/scram-client: pgjdbc: Man-in-the-middle protec |

### Misconfigurations

| Rule | File | Issue | Resolution |
|---|---|---|---|
| DS-0002 | stubs/docker/apache-flink/Dockerfile | Image user should not be 'root' | Add 'USER <non root user name>' line to the Dockerfile |
| DS-0017 | stubs/docker/apache-flink/Dockerfile:22 | 'RUN <package-manager> update' instruction alone | Combine '<package-manager> update' and '<package-manager> install' instructions to single one |
| DS-0029 | stubs/docker/apache-flink/Dockerfile:23 | 'apt-get' missing '--no-install-recommends' | Add '--no-install-recommends' flag to 'apt-get' |
| DS-0029 | stubs/docker/apache-flink/Dockerfile:26 | 'apt-get' missing '--no-install-recommends' | Add '--no-install-recommends' flag to 'apt-get' |

## CRITICAL

### Vulnerabilities

| ID | Package | Installed | Fixed | Target | Title |
|---|---|---|---|---|---|
| CVE-2019-17571 | log4j:log4j | 1.2.17 | **no fix** | data-products/pom.xml | log4j: deserialization of untrusted data in SocketServer |
| CVE-2022-23305 | log4j:log4j | 1.2.17 | **no fix** | data-products/pom.xml | log4j: SQL injection in Log4j 1.x when application is configured to use JDBCAppender |
| CVE-2022-23307 | log4j:log4j | 1.2.17 | **no fix** | data-products/pom.xml | log4j: Unsafe deserialization flaw in Chainsaw log viewer |
| CVE-2022-42889 | org.apache.commons:commons-text | 1.6 | 1.10.0 | data-products/pom.xml | apache-commons-text: variable interpolation RCE |
| CVE-2021-44228 | org.apache.logging.log4j:log4j-core | 2.14.1 | 2.15.0, 2.3.1, 2.12.2 | data-products/pom.xml | log4j-core: Remote code execution in Log4j 2.x when logs contain an attacker-controlled st |
| CVE-2021-45046 | org.apache.logging.log4j:log4j-core | 2.14.1 | 2.16.0, 2.12.2 | data-products/pom.xml | log4j-core: DoS in log4j 2.x with thread context message pattern and context lookup patter |
| CVE-2023-44981 | org.apache.zookeeper:zookeeper | 3.5.9 | 3.7.2, 3.8.3, 3.9.1 | data-products/pom.xml | zookeeper: Authorization Bypass in Apache ZooKeeper |

### Misconfigurations

| Rule | File | Issue | Resolution |
|---|---|---|---|
| DS-0031 | stubs/docker/apache-flink/Dockerfile:51 | Secrets passed via `build-args` or envs or copied secret files | Use secret mount if secrets are needed during image build. Use volume mount if secret files are needed during container runtime. |

---
_Note: identical CVE+package pairs appearing in multiple pom.xml targets are listed once with all targets; the raw per-target count is 117. log4j 1.2.17 findings have no fixed version (EOL) — remediation is exclusion/removal._

---

## Image scan (Phase 3/4) — 2026-08-13

Built `unified-image` and scanned it (`obsrv/unified:trivy-fix`, 678 MB).

| Layer | Findings | Fixable |
|---|---|---|
| OS packages (Debian 13.6, 66 pkgs from the DHI base) | CRITICAL 4 · HIGH 13 · MEDIUM 63 · LOW 68 = 148 | **0** |
| Java / jars | MEDIUM 5 | 5 → **fixed** |
| Secrets | 0 | — |

**Application jar clean:** `unified-pipeline-1.0.0.jar` produced zero findings — the source remediation carried into the built artifact.

**The 5 jar findings came from Flink, not from our code:** `log4j-core-2.24.3.jar` (CVE-2025-68161, CVE-2026-34477/34478/34480) and `log4j-1.2-api-2.24.3.jar` (CVE-2026-34479) ship inside the official Flink 1.20.5 distribution in `/opt/flink/lib`, so the pom pins could not affect them.

**Fix:** the `flink-dist` stage now downloads log4j `${LOG4J_VERSION}` (2.25.4) for all four artifacts (api, core, 1.2-api, slf4j-impl — replaced together to keep the runtime version-consistent) and deletes the 2.24.3 jars. The stage asserts no `2.24.3` jar survives, so a regression fails the build. `LOG4J_VERSION` is a **global** ARG (declared before the first `FROM`, or it scopes to the preceding stage) and must stay in sync with `<log4j.version>` in the root pom.

Verified by building the `flink-dist` stage and scanning it: log4j findings **0**, fixable jar findings **0**.

**The 148 OS findings have no upstream patch** — not one has a fixed version from Debian, including the 4 CRITICALs (all `perl-base 5.40.1-6+dhi6`). Expected for a hardened base; scan images with `--ignore-unfixed` to see only actionable results.

### Per-image verification (2026-08-13)

Built and scanned individually, clearing each image before the next:

| Image | Size | Total | **Fixable** | log4j | Jar findings | Secrets |
|---|---|---|---|---|---|---|
| extractor | 1.61 GB | 148 | **0** | 0 | 0 | 0 |
| preprocessor | 1.61 GB | 148 | **0** | 0 | 0 | 0 |
| denormalizer | 1.62 GB | 148 | **0** | 0 | 0 | 0 |
| transformer | 1.62 GB | 148 | **0** | 0 | 0 | 0 |
| dataset-router | 1.62 GB | 148 | **0** | 0 | 0 | 0 |
| unified-pipeline | 678 MB | 153 → 148 | **0** (via flink-dist stage scan) | 0 | 0 | 0 |
| cache-indexer | — | — | not built locally (disk) | — | — | — |

The identical 148 total on every image is the DHI base's unfixed OS-package set — no upstream patch exists for any of them. Confirmed shipping `log4j-api/core/1.2-api/slf4j-impl` all at **2.25.4**.

**Build constraint:** the two in-container Maven stages need ~8 GB of scratch, and each image adds a ~1.6 GB Flink-dist layer. On an ~18 GB volume this repeatedly exhausted the disk and crashed the OrbStack VM (`StorageFull`). Build all seven in CI (the workflow already does, on ubuntu-latest) or on a host with 20+ GB free. To reproduce locally: build `--target build-pipeline` once, then loop the image targets, deleting each after its scan.

# =============================================================================
# Hardened Flink runtime for obsrv-core (unified-pipeline + cache-indexer + lakehouse-connector)
# -----------------------------------------------------------------------------
# WHY the choices here (learned the hard way):
#   * DHI has NO Flink runtime base and NO maven-jdk11. BUILD stages stay on the
#     original maven image (multi-stage: discarded, not shipped).
#   * The shipped RUNTIME uses the DHI eclipse-temurin 11 JDK **debian** (glibc)
#     image, NOT the alpine `11-jre`. Reasons the alpine base failed:
#       - Flink launch scripts need bash (alpine has only busybox ash).
#       - Hadoop S3 login needs the running uid in /etc/passwd (getpwuid).
#       - snappy-java's bundled native lib is glibc-linked (needs
#         ld-linux-x86-64.so.2); on musl/alpine it fails and every checkpoint
#         (snapshot-compression=true) dies.
#     The debian (glibc) DHI image ships bash + coreutils + glibc, so it just
#     works — we only add a flink:9999 passwd entry and own the dist.
#   * Flink dist is taken from the official flink image (version-matched, has
#     the s3 plugin), then carried onto the hardened runtime.
# =============================================================================
ARG FLINK_UID=9999
# keep in sync with <log4j.version> in the root pom.xml
ARG LOG4J_VERSION=2.25.5

# ---- build stages: compile obsrv-core (original maven, discarded) ------------
FROM public.ecr.aws/docker/library/maven:3.9.4-eclipse-temurin-11-focal AS build-core
COPY . /app
RUN mvn clean install -DskipTests -f /app/pom.xml

FROM public.ecr.aws/docker/library/maven:3.9.4-eclipse-temurin-11-focal AS build-pipeline
COPY --from=build-core /root/.m2 /root/.m2
COPY . /app
RUN mvn clean package -DskipTests -f /app/pipeline/pom.xml

# ---- download-hudi-plugins: extra JARs for hudi-connector's S3/GCS plugin classloaders -------
# Not produced by the maven build above - flink-shaded-hadoop-2-uber, flink-gs-fs-hadoop and
# gcs-connector are pre-built artifacts pulled straight from Maven Central, decoupled from the
# source compile so this stage doesn't need to rerun if only source changes.
#
# s3-fs-hadoop and gs-fs-hadoop each get their own subdir - Flink loads each plugins/<dir>
# with its own isolated classloader. flink-s3-fs-hadoop-*.jar bundles its own unshaded
# jackson-databind:2.15.3 (real com.fasterxml.jackson.databind package, not relocated); in
# lib/ (parent-first, shared classpath) that collided with hudi-connector-1.0.0.jar's
# jackson-module-scala:2.17.2, which hard-checks its databind version at runtime and threw
# "Scala module 2.17.2 requires Jackson Databind version >= 2.17.0 and < 2.18.0 - Found
# jackson-databind version 2.15.3" the moment HudiSchemaParser built an ObjectMapper.
# Isolating s3-fs-hadoop (+ its own flink-shaded-hadoop-2-uber dep) into its own plugin
# classloader keeps its bundled Jackson out of lib/'s shared classpath entirely. gs-fs-hadoop
# gets the same isolated treatment, which also keeps its Hadoop 3 classes away from
# s3-fs-hadoop's Hadoop 2 ones instead of mixing both in lib/.
#
# flink-shaded-hadoop-2-uber ALSO goes into /jars (-> lib/) as a second copy: hudi-connector's
# flink-connector-hive dependency bundles HiveServer2DelegationTokenProvider, auto-registered
# via ServiceLoader, whose constructor needs org.apache.hadoop.conf.Configuration resolvable
# from lib/'s classloader - moving the uber jar fully into plugins/ (isolated from lib/) broke
# that. This jar's own Jackson is relocated under org.apache.htrace.* (verified - not the real
# com.fasterxml.jackson package), so a second copy in lib/ doesn't reintroduce the Jackson
# conflict the plugins/ move was fixing in the first place.
#
# gcs-connector-hadoop3-*-shaded.jar (unlike flink-s3-fs-hadoop) relocates its own Jackson AND
# Guava under com/google/cloud/hadoop/repackaged/gcs/* (verified via unzip - zero unrelocated
# com.fasterxml.jackson/com.google.common classes), so a second copy in lib/ is safe: same
# ClassNotFoundException-for-Hudi's-direct-FileSystem.get() problem as S3AFileSystem, same fix,
# but no pom.xml dependency-exclusion dance needed since this jar doesn't collide.
FROM --platform=linux/amd64 public.ecr.aws/docker/library/maven:3.9.4-eclipse-temurin-11-focal AS download-hudi-plugins
RUN mkdir -p /plugins/s3-fs-hadoop /plugins/gs-fs-hadoop /jars && \
    curl -fsSL -o /plugins/s3-fs-hadoop/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar \
        https://repo1.maven.org/maven2/org/apache/flink/flink-shaded-hadoop-2-uber/2.8.3-10.0/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar && \
    cp /plugins/s3-fs-hadoop/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar /jars/ && \
    curl -fsSL -o /plugins/gs-fs-hadoop/flink-gs-fs-hadoop-1.20.5.jar \
        https://repo1.maven.org/maven2/org/apache/flink/flink-gs-fs-hadoop/1.20.5/flink-gs-fs-hadoop-1.20.5.jar && \
    curl -fsSL -o /plugins/gs-fs-hadoop/gcs-connector-hadoop3-2.2.11-shaded.jar \
        https://repo1.maven.org/maven2/com/google/cloud/bigdataoss/gcs-connector/hadoop3-2.2.11/gcs-connector-hadoop3-2.2.11-shaded.jar && \
    cp /plugins/gs-fs-hadoop/gcs-connector-hadoop3-2.2.11-shaded.jar /jars/ && \
    echo "Jackson jars intentionally omitted — hudi-flink bundle ships its own databind"

# ---- flink-dist stage: exact Flink 1.20 dist from the official image ---------
# Pinned to the exact patch hudi-connector/pom.xml compiles against (1.20.5), not main's own
# floating "1.20" tag - avoids three different Flink patch versions across this one image
# (pom, gs-fs-hadoop, dist).
FROM public.ecr.aws/docker/library/flink:1.20.5-scala_2.12-java11 AS flink-dist
ARG FLINK_UID
ENV FLINK_HOME=/opt/flink
USER root
RUN set -eux; \
    mkdir -p "${FLINK_HOME}/usrlib" "${FLINK_HOME}/plugins/s3-fs-hadoop"; \
    mv "${FLINK_HOME}"/opt/flink-s3-fs-hadoop-*.jar "${FLINK_HOME}/plugins/s3-fs-hadoop/"; \
    chown -R ${FLINK_UID}:${FLINK_UID} "${FLINK_HOME}"

# Replace the log4j jars bundled in the Flink dist (CVE-2026-49844 and earlier)
ARG LOG4J_VERSION
RUN set -eux; \
    BASE="https://repo1.maven.org/maven2/org/apache/logging/log4j"; \
    for A in log4j-api log4j-core log4j-1.2-api log4j-slf4j-impl; do \
      curl -fsSL -o "${FLINK_HOME}/lib/${A}-${LOG4J_VERSION}.jar" \
        "${BASE}/${A}/${LOG4J_VERSION}/${A}-${LOG4J_VERSION}.jar"; \
      find "${FLINK_HOME}/lib" -name "${A}-2.*.jar" ! -name "${A}-${LOG4J_VERSION}.jar" -delete; \
    done; \
    ls "${FLINK_HOME}/lib/" | grep -q "log4j-core-${LOG4J_VERSION}.jar"; \
    ! ls "${FLINK_HOME}/lib/" | grep -qE 'log4j-core-(2\.2[0-4]|1\.)'; \
    chown -R ${FLINK_UID}:${FLINK_UID} "${FLINK_HOME}/lib"
    # No hardcoded s3.aws.credentials.provider here (main's own Dockerfile still has one:
    # WebIdentityTokenCredentialsProvider only) - the original pre-DHI Dockerfile had no
    # credentials-provider override at all, relying on Hadoop-S3A's own default provider chain
    # to try key-based SimpleAWSCredentialsProvider (fs.s3a.access.key/secret.key from
    # core-site.xml - what MinIO needs) first, falling through to
    # WebIdentityTokenCredentialsProvider/instance-profile for real AWS/IRSA if no keys are set.
    # Forcing one provider broke MinIO: it went straight to EnvironmentVariableCredentialsProvider
    # on the checkpoint S3 client and failed, since keys were never even tried. Confirmed via a
    # real cluster redeploy against MinIO - fixed here for every image built from this stage,
    # not just lakehouse-connector-image.

# =============================================================================
# extractor runtime
# =============================================================================
FROM dhi.io/eclipse-temurin:11-jdk-debian13-dev AS extractor-image
ARG FLINK_UID
ENV FLINK_HOME=/opt/flink
ENV PATH="${FLINK_HOME}/bin:${PATH}"
USER 0
COPY --from=flink-dist --chown=${FLINK_UID}:${FLINK_UID} /opt/flink /opt/flink
COPY --from=build-pipeline --chown=${FLINK_UID}:${FLINK_UID} /app/pipeline/extractor/target/extractor-1.0.0.jar ${FLINK_HOME}/usrlib/
RUN printf 'flink:x:%s:%s:flink:/opt/flink:/bin/bash\n' "${FLINK_UID}" "${FLINK_UID}" >> /etc/passwd \
    && printf 'flink:x:%s:\n' "${FLINK_UID}" >> /etc/group
USER ${FLINK_UID}:${FLINK_UID}
WORKDIR ${FLINK_HOME}
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD bash -c "pgrep -f org.apache.flink >/dev/null || java -version >/dev/null 2>&1 || exit 1"

# =============================================================================
# preprocessor runtime
# =============================================================================
FROM dhi.io/eclipse-temurin:11-jdk-debian13-dev AS preprocessor-image
ARG FLINK_UID
ENV FLINK_HOME=/opt/flink
ENV PATH="${FLINK_HOME}/bin:${PATH}"
USER 0
COPY --from=flink-dist --chown=${FLINK_UID}:${FLINK_UID} /opt/flink /opt/flink
COPY --from=build-pipeline --chown=${FLINK_UID}:${FLINK_UID} /app/pipeline/preprocessor/target/preprocessor-1.0.0.jar ${FLINK_HOME}/usrlib/
RUN printf 'flink:x:%s:%s:flink:/opt/flink:/bin/bash\n' "${FLINK_UID}" "${FLINK_UID}" >> /etc/passwd \
    && printf 'flink:x:%s:\n' "${FLINK_UID}" >> /etc/group
USER ${FLINK_UID}:${FLINK_UID}
WORKDIR ${FLINK_HOME}
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD bash -c "pgrep -f org.apache.flink >/dev/null || java -version >/dev/null 2>&1 || exit 1"

# =============================================================================
# denormalizer runtime
# =============================================================================
FROM dhi.io/eclipse-temurin:11-jdk-debian13-dev AS denormalizer-image
ARG FLINK_UID
ENV FLINK_HOME=/opt/flink
ENV PATH="${FLINK_HOME}/bin:${PATH}"
USER 0
COPY --from=flink-dist --chown=${FLINK_UID}:${FLINK_UID} /opt/flink /opt/flink
COPY --from=build-pipeline --chown=${FLINK_UID}:${FLINK_UID} /app/pipeline/denormalizer/target/denormalizer-1.0.0.jar ${FLINK_HOME}/usrlib/
RUN printf 'flink:x:%s:%s:flink:/opt/flink:/bin/bash\n' "${FLINK_UID}" "${FLINK_UID}" >> /etc/passwd \
    && printf 'flink:x:%s:\n' "${FLINK_UID}" >> /etc/group
USER ${FLINK_UID}:${FLINK_UID}
WORKDIR ${FLINK_HOME}
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD bash -c "pgrep -f org.apache.flink >/dev/null || java -version >/dev/null 2>&1 || exit 1"

# =============================================================================
# transformer runtime
# =============================================================================
FROM dhi.io/eclipse-temurin:11-jdk-debian13-dev AS transformer-image
ARG FLINK_UID
ENV FLINK_HOME=/opt/flink
ENV PATH="${FLINK_HOME}/bin:${PATH}"
USER 0
COPY --from=flink-dist --chown=${FLINK_UID}:${FLINK_UID} /opt/flink /opt/flink
COPY --from=build-pipeline --chown=${FLINK_UID}:${FLINK_UID} /app/pipeline/transformer/target/transformer-1.0.0.jar ${FLINK_HOME}/usrlib/
RUN printf 'flink:x:%s:%s:flink:/opt/flink:/bin/bash\n' "${FLINK_UID}" "${FLINK_UID}" >> /etc/passwd \
    && printf 'flink:x:%s:\n' "${FLINK_UID}" >> /etc/group
USER ${FLINK_UID}:${FLINK_UID}
WORKDIR ${FLINK_HOME}
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD bash -c "pgrep -f org.apache.flink >/dev/null || java -version >/dev/null 2>&1 || exit 1"

# =============================================================================
# dataset-router runtime
# =============================================================================
FROM dhi.io/eclipse-temurin:11-jdk-debian13-dev AS dataset-router-image
ARG FLINK_UID
ENV FLINK_HOME=/opt/flink
ENV PATH="${FLINK_HOME}/bin:${PATH}"
USER 0
COPY --from=flink-dist --chown=${FLINK_UID}:${FLINK_UID} /opt/flink /opt/flink
COPY --from=build-pipeline --chown=${FLINK_UID}:${FLINK_UID} /app/pipeline/dataset-router/target/dataset-router-1.0.0.jar ${FLINK_HOME}/usrlib/
RUN printf 'flink:x:%s:%s:flink:/opt/flink:/bin/bash\n' "${FLINK_UID}" "${FLINK_UID}" >> /etc/passwd \
    && printf 'flink:x:%s:\n' "${FLINK_UID}" >> /etc/group
USER ${FLINK_UID}:${FLINK_UID}
WORKDIR ${FLINK_HOME}
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD bash -c "pgrep -f org.apache.flink >/dev/null || java -version >/dev/null 2>&1 || exit 1"

# =============================================================================
# unified-pipeline runtime  (DHI debian/glibc JDK 11 — has bash, coreutils, glibc)
# =============================================================================
FROM dhi.io/eclipse-temurin:11-jdk-debian13-dev AS unified-image
ARG FLINK_UID
ENV FLINK_HOME=/opt/flink
ENV PATH="${FLINK_HOME}/bin:${PATH}"
USER 0
COPY --from=flink-dist --chown=${FLINK_UID}:${FLINK_UID} /opt/flink /opt/flink
COPY --from=build-pipeline --chown=${FLINK_UID}:${FLINK_UID} /app/pipeline/unified-pipeline/target/unified-pipeline-1.0.0.jar ${FLINK_HOME}/usrlib/
# flink user so Hadoop getpwuid(9999) resolves for S3 login
RUN printf 'flink:x:%s:%s:flink:/opt/flink:/bin/bash\n' "${FLINK_UID}" "${FLINK_UID}" >> /etc/passwd \
    && printf 'flink:x:%s:\n' "${FLINK_UID}" >> /etc/group
USER ${FLINK_UID}:${FLINK_UID}
WORKDIR ${FLINK_HOME}
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD bash -c "pgrep -f org.apache.flink >/dev/null || java -version >/dev/null 2>&1 || exit 1"

# =============================================================================
# cache-indexer runtime
# =============================================================================
FROM dhi.io/eclipse-temurin:11-jdk-debian13-dev AS cache-indexer-image
ARG FLINK_UID
ENV FLINK_HOME=/opt/flink
ENV PATH="${FLINK_HOME}/bin:${PATH}"
USER 0
COPY --from=flink-dist --chown=${FLINK_UID}:${FLINK_UID} /opt/flink /opt/flink
COPY --from=build-pipeline --chown=${FLINK_UID}:${FLINK_UID} /app/pipeline/cache-indexer/target/cache-indexer-1.0.0.jar ${FLINK_HOME}/usrlib/
RUN printf 'flink:x:%s:%s:flink:/opt/flink:/bin/bash\n' "${FLINK_UID}" "${FLINK_UID}" >> /etc/passwd \
    && printf 'flink:x:%s:\n' "${FLINK_UID}" >> /etc/group
USER ${FLINK_UID}:${FLINK_UID}
WORKDIR ${FLINK_HOME}
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD bash -c "pgrep -f org.apache.flink >/dev/null || java -version >/dev/null 2>&1 || exit 1"

# =============================================================================
# lakehouse-connector runtime (DHI debian/glibc JDK 11 — has bash + coreutils)
# =============================================================================
FROM dhi.io/eclipse-temurin:11-jdk-debian13-dev AS lakehouse-connector-image
ARG FLINK_UID
ENV FLINK_HOME=/opt/flink
ENV PATH="${FLINK_HOME}/bin:${PATH}"
# Plugin classloaders (plugins/s3-fs-hadoop/, plugins/gs-fs-hadoop/) don't inherit the main
# classpath's /opt/hadoop/etc/hadoop entry, so isolating s3-fs-hadoop there (for the earlier
# Jackson fix) also cut it off from core-site.xml, where fs.s3a.access.key/secret.key live for
# MinIO. HADOOP_CONF_DIR is Hadoop's own env-var-based config lookup, checked independent of
# whichever classloader is running, so this restores core-site.xml visibility for every plugin.
# Confirmed redundant for chart-based deploys specifically: helmcharts/services/lakehouse-
# connector's own pod spec has set this same env var on both jobmanager/taskmanager since
# before this PR, and pod-spec env: overrides image ENV - so for that deploy path the actual
# fix for the MinIO credentials crash was removing the misnamed s3.aws.credentials.provider
# hardcode above (in flink-dist), not this line. Kept anyway as real, non-redundant coverage
# for any deploy path that doesn't go through that chart (bare docker run, a different
# orchestrator, etc.).
ENV HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop
USER 0
RUN apt-get update -qq && apt-get install -y --no-install-recommends gettext-base && rm -rf /var/lib/apt/lists/*
COPY --from=flink-dist /docker-entrypoint.sh /docker-entrypoint.sh
COPY --from=flink-dist --chown=${FLINK_UID}:${FLINK_UID} /opt/flink /opt/flink
COPY --from=download-hudi-plugins --chown=${FLINK_UID}:${FLINK_UID} /plugins/s3-fs-hadoop/ ${FLINK_HOME}/plugins/s3-fs-hadoop/
COPY --from=download-hudi-plugins --chown=${FLINK_UID}:${FLINK_UID} /plugins/gs-fs-hadoop/ ${FLINK_HOME}/plugins/gs-fs-hadoop/
COPY --from=download-hudi-plugins --chown=${FLINK_UID}:${FLINK_UID} /jars/ ${FLINK_HOME}/lib/
COPY --from=build-pipeline --chown=${FLINK_UID}:${FLINK_UID} /app/pipeline/hudi-connector/target/hudi-connector-1.0.0.jar ${FLINK_HOME}/lib/
RUN chmod +x /docker-entrypoint.sh \
    && printf 'flink:x:%s:%s:flink:/opt/flink:/bin/bash\n' "${FLINK_UID}" "${FLINK_UID}" >> /etc/passwd \
    && printf 'flink:x:%s:\n' "${FLINK_UID}" >> /etc/group
USER ${FLINK_UID}:${FLINK_UID}
WORKDIR ${FLINK_HOME}
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD bash -c "pgrep -f org.apache.flink >/dev/null || java -version >/dev/null 2>&1 || exit 1"
EXPOSE 6123 8081
ENTRYPOINT ["/docker-entrypoint.sh"]
# Matches the official Flink image's own pairing: without a CMD, `docker run <image>` with no
# args hits the entrypoint's pass-through `exec "$@"` with empty argv and exits 0 immediately.
CMD ["help"]

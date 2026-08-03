# =============================================================================
# Hardened Flink runtime for obsrv-core (lakehouse-connector)
# -----------------------------------------------------------------------------
# * Build stages stay on the original maven image (discarded, not shipped).
# * Flink dist is taken from the official flink image (version-matched, has
#   the s3 plugin) — avoids downloading from archive.apache.org.
# * Runtime uses dhi.io/eclipse-temurin:11-jdk-debian13-dev which has bash,
#   coreutils and glibc so Flink scripts and snappy-java work correctly.
# =============================================================================
ARG FLINK_UID=9999

# ---- build: compile hudi-connector and its dependencies only ----------------
FROM public.ecr.aws/docker/library/maven:3.9.4-eclipse-temurin-11-focal AS build-pipeline
COPY . /app
RUN mvn -pl pipeline/hudi-connector -am clean package -DskipTests -q -f /app/pom.xml

# ---- flink-dist: Flink 1.20 dist + extra jars for lakehouse -----------------
FROM public.ecr.aws/docker/library/flink:1.20-scala_2.12-java11 AS flink-dist
ARG FLINK_UID
ENV FLINK_HOME=/opt/flink
USER root
RUN set -eux; \
    mkdir -p "${FLINK_HOME}/usrlib" "${FLINK_HOME}/plugins/flink-s3-fs-hadoop"; \
    mv "${FLINK_HOME}"/opt/flink-s3-fs-hadoop-*.jar "${FLINK_HOME}/plugins/flink-s3-fs-hadoop/"; \
    if [ -f "${FLINK_HOME}/conf/config.yaml" ]; then CONF="${FLINK_HOME}/conf/config.yaml"; \
    else CONF="${FLINK_HOME}/conf/flink-conf.yaml"; fi; \
    echo 's3.aws.credentials.provider: com.amazonaws.auth.WebIdentityTokenCredentialsProvider' >> "${CONF}"; \
    wget -nv -P "${FLINK_HOME}/lib/" \
        https://repo1.maven.org/maven2/org/apache/flink/flink-shaded-hadoop-2-uber/2.8.3-10.0/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar \
        https://repo.maven.apache.org/maven2/org/apache/hudi/hudi-flink1.20-bundle/1.0.2/hudi-flink1.20-bundle-1.0.2.jar \
        https://repo1.maven.org/maven2/org/apache/flink/flink-gs-fs-hadoop/1.20.1/flink-gs-fs-hadoop-1.20.1.jar \
        https://repo1.maven.org/maven2/com/google/cloud/bigdataoss/gcs-connector/hadoop3-2.2.11/gcs-connector-hadoop3-2.2.11-shaded.jar \
        https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.13.4/jackson-databind-2.13.4.jar \
        https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.13.4/jackson-core-2.13.4.jar \
        https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.13.4/jackson-annotations-2.13.4.jar; \
    chown -R ${FLINK_UID}:${FLINK_UID} "${FLINK_HOME}"

# =============================================================================
# lakehouse-connector runtime (DHI debian/glibc JDK 11 — has bash + coreutils)
# =============================================================================
FROM dhi.io/eclipse-temurin:11-jdk-debian13-dev AS lakehouse-connector-image
ARG FLINK_UID
ENV FLINK_HOME=/opt/flink
ENV PATH="${FLINK_HOME}/bin:${PATH}"
USER 0
COPY --from=flink-dist /docker-entrypoint.sh /docker-entrypoint.sh
COPY --from=flink-dist --chown=${FLINK_UID}:${FLINK_UID} /opt/flink /opt/flink
COPY --from=build-pipeline --chown=${FLINK_UID}:${FLINK_UID} /app/pipeline/hudi-connector/target/hudi-connector-1.0.0.jar ${FLINK_HOME}/lib/
RUN chmod +x /docker-entrypoint.sh \
    && printf 'flink:x:%s:%s:flink:/opt/flink:/bin/bash\n' "${FLINK_UID}" "${FLINK_UID}" >> /etc/passwd \
    && printf 'flink:x:%s:\n' "${FLINK_UID}" >> /etc/group
USER ${FLINK_UID}:${FLINK_UID}
WORKDIR ${FLINK_HOME}
ENTRYPOINT ["/docker-entrypoint.sh"]

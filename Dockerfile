# Stage 1: Build hudi-connector JAR
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .
RUN mvn -pl pipeline/hudi-connector -am clean package -DskipTests -q

# Stage 2: Pull hardened Java from DHI eclipse-temurin image
FROM dhi.io/eclipse-temurin:11-debian13 AS java-provider

# Stage 3: Flink 1.20 on Debian 13 slim with DHI Java copied in
FROM debian:13-slim AS flink-base

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl wget gpg libsnappy1v5 gettext-base libjemalloc-dev ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=java-provider /opt/java /opt/java
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH

ENV GOSU_VERSION=1.17
RUN set -ex; \
    dpkgArch="$(dpkg --print-architecture | awk -F- '{ print $NF }')"; \
    wget -nv -O /usr/local/bin/gosu "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$dpkgArch"; \
    chmod +x /usr/local/bin/gosu; \
    gosu nobody true

ENV FLINK_VERSION=1.20.1
ENV FLINK_HOME=/opt/flink
ENV PATH=$FLINK_HOME/bin:$PATH

RUN groupadd --system --gid=9999 flink && \
    useradd --system --home-dir $FLINK_HOME --uid=9999 --gid=flink flink
WORKDIR $FLINK_HOME

RUN set -ex; \
    wget -nv -O flink.tgz "https://archive.apache.org/dist/flink/flink-${FLINK_VERSION}/flink-${FLINK_VERSION}-bin-scala_2.12.tgz"; \
    tar -xf flink.tgz --strip-components=1; \
    rm flink.tgz; \
    chown -R flink:flink .

# Stage 4: Lakehouse connector — layer hudi jars on top of Flink 1.20
FROM flink-base AS lakehouse-connector-image

USER flink

RUN wget -nv https://repo1.maven.org/maven2/org/apache/flink/flink-shaded-hadoop-2-uber/2.8.3-10.0/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar && \
    wget -nv https://repo1.maven.org/maven2/org/apache/flink/flink-s3-fs-hadoop/${FLINK_VERSION}/flink-s3-fs-hadoop-${FLINK_VERSION}.jar && \
    wget -nv https://repo.maven.apache.org/maven2/org/apache/hudi/hudi-flink1.20-bundle/1.0.2/hudi-flink1.20-bundle-1.0.2.jar && \
    wget -nv https://repo1.maven.org/maven2/org/apache/flink/flink-gs-fs-hadoop/${FLINK_VERSION}/flink-gs-fs-hadoop-${FLINK_VERSION}.jar && \
    wget -nv https://repo1.maven.org/maven2/com/google/cloud/bigdataoss/gcs-connector/hadoop3-2.2.11/gcs-connector-hadoop3-2.2.11-shaded.jar && \
    mv flink-shaded-hadoop-2-uber-2.8.3-10.0.jar $FLINK_HOME/lib/ && \
    mv flink-s3-fs-hadoop-${FLINK_VERSION}.jar $FLINK_HOME/lib/ && \
    mv hudi-flink1.20-bundle-1.0.2.jar $FLINK_HOME/lib/ && \
    mv flink-gs-fs-hadoop-${FLINK_VERSION}.jar $FLINK_HOME/lib/ && \
    mv gcs-connector-hadoop3-2.2.11-shaded.jar $FLINK_HOME/lib/

COPY --from=builder /build/pipeline/hudi-connector/target/hudi-connector-1.0.0.jar $FLINK_HOME/lib/

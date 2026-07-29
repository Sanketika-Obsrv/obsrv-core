# Stage 1: Build Flink 1.20 on DHI Eclipse Temurin base
FROM dhi/eclipse-temurin:17-jdk AS flink-base

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl wget gpg libsnappy1v5 gettext-base libjemalloc-dev \
    && rm -rf /var/lib/apt/lists/*

# Grab gosu for easy step-down from root
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

# Stage 2: Lakehouse connector — layer hudi jars on top of Flink 1.20
FROM flink-base AS lakehouse-connector

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

COPY pipeline/hudi-connector/target/hudi-connector-1.0.0.jar $FLINK_HOME/lib/

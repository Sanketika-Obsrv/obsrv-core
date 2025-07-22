FROM maven:3.9.4-eclipse-temurin-11-focal AS build-core
COPY . /app
RUN mvn clean install -DskipTests -f /app/pom.xml
# RUN mvn clean install -DskipTests -f /app/dataset-registry/pom.xml
# RUN mvn clean install -DskipTests -f /app/transformation-sdk/pom.xml

FROM maven:3.9.4-eclipse-temurin-11-focal AS build-pipeline
COPY --from=build-core /root/.m2 /root/.m2
COPY . /app
RUN mvn clean package -DskipTests -f /app/pipeline/pom.xml

# FROM sanketikahub/flink:1.20-scala_2.12-java11 AS extractor-image
# USER flink
# RUN mkdir -p $FLINK_HOME/usrlib
# COPY --from=build-pipeline /app/pipeline/extractor/target/extractor-1.0.0.jar $FLINK_HOME/usrlib/

# FROM sanketikahub/flink:1.20-scala_2.12-java11 AS preprocessor-image
# USER flink
# RUN mkdir -p $FLINK_HOME/usrlib
# COPY --from=build-pipeline /app/pipeline/preprocessor/target/preprocessor-1.0.0.jar $FLINK_HOME/usrlib/

# FROM sanketikahub/flink:1.20-scala_2.12-java11 AS denormalizer-image
# USER flink
# RUN mkdir -p $FLINK_HOME/usrlib
# COPY --from=build-pipeline /app/pipeline/denormalizer/target/denormalizer-1.0.0.jar $FLINK_HOME/usrlib/

# FROM sanketikahub/flink:1.20-scala_2.12-java11 AS transformer-image
# USER flink
# RUN mkdir -p $FLINK_HOME/usrlib
# COPY --from=build-pipeline /app/pipeline/transformer/target/transformer-1.0.0.jar $FLINK_HOME/usrlib/

# FROM sanketikahub/flink:1.20-scala_2.12-java11 AS dataset-router-image
# USER flink
# RUN mkdir -p $FLINK_HOME/usrlib
# COPY --from=build-pipeline /app/pipeline/dataset-router/target/dataset-router-1.0.0.jar $FLINK_HOME/usrlib/

# # unified image build
# FROM sanketikahub/flink:1.20-scala_2.12-java11 AS unified-image
# USER flink
# RUN mkdir -p $FLINK_HOME/usrlib
# COPY --from=build-pipeline /app/pipeline/unified-pipeline/target/unified-pipeline-1.0.0.jar $FLINK_HOME/usrlib/

# hudi connector image build

# FROM maven:3.9.4-eclipse-temurin-11-focal AS build-hudi-connector
# WORKDIR /home/flink
# RUN git clone https://github.com/Sanketika-Obsrv/obsrv-core.git \
#     && cd obsrv-core \
#     && git checkout tags/1.7.1 \
#     && mvn clean install -DskipTests -f framework/pom.xml \
#     && mvn clean install -DskipTests -f dataset-registry/pom.xml
# #RUN mkdir -p /home/flink/hudi-connector
# RUN mkdir -p /home/flink/pipeline
# COPY --from=build-pipeline /app/pipeline /home/flink/pipeline/
# RUN mvn clean install -DskipTests -pl /home/flink/pipeline/hudi-connector -am -f /home/flink/pipeline/hudi-connector/pom.xml

FROM maven:3.9.4-eclipse-temurin-11-focal AS build-base-1.7.1
WORKDIR /app
# Use git to checkout specific tag cleanly
RUN git clone https://github.com/Sanketika-Obsrv/obsrv-core.git . && \
    git checkout tags/1.7.1
# Install only framework and dataset-registry
RUN mvn clean install -DskipTests -pl framework,dataset-registry -am


FROM maven:3.9.4-eclipse-temurin-11-focal AS build-hudi-connector
# Copy full source code of 2.0.0 (not just one module!)
COPY . /home/flink
WORKDIR /home/flink
# Checkout to tag 2.0.0 if needed (already in the correct codebase otherwise skip)
# RUN git checkout tags/2.0.0
# Use pre-built 1.7.1 framework and dataset-registry jars
COPY --from=build-base-1.7.1 /root/.m2 /root/.m2
# Build hudi-connector with its parents present
# RUN mvn clean install -DskipTests -pl pipeline/hudi-connector -am

RUN mvn clean install -DskipTests -pl pipeline/hudi-connector -am -f pom.xml


# Lakehouse connector image build
FROM sanketikahub/flink:1.17.2-scala_2.12-java11 AS lakehouse-connector-image
USER flink
RUN wget https://repo1.maven.org/maven2/org/apache/flink/flink-shaded-hadoop-2-uber/2.8.3-10.0/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar
RUN wget https://repo1.maven.org/maven2/org/apache/flink/flink-s3-fs-hadoop/1.17.2/flink-s3-fs-hadoop-1.17.2.jar
RUN wget https://repo1.maven.org/maven2/org/apache/hudi/hudi-flink1.17.x/1.0.2/hudi-flink1.17.x-1.0.2.jar

RUN mv flink-shaded-hadoop-2-uber-2.8.3-10.0.jar $FLINK_HOME/lib
RUN mv flink-s3-fs-hadoop-1.17.2.jar $FLINK_HOME/lib
RUN mv hudi-flink1.17.x-1.0.2.jar $FLINK_HOME/lib

COPY --from=build-hudi-connector /home/flink/pipeline/hudi-connector/target/hudi-connector-1.0.0.jar $FLINK_HOME/lib/

# cache indexer image build
# FROM sanketikahub/flink:1.20-scala_2.12-java11 AS cache-indexer-image
# USER flink
# RUN mkdir -p $FLINK_HOME/usrlib
# COPY --from=build-pipeline /app/pipeline/cache-indexer/target/cache-indexer-1.0.0.jar $FLINK_HOME/usrlib/
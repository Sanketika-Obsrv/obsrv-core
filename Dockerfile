FROM sanketikahub/flink:1.17.2-scala_2.12-java11 AS lakehouse-connector
USER flink
RUN wget https://repo1.maven.org/maven2/org/apache/flink/flink-shaded-hadoop-2-uber/2.8.3-10.0/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar
RUN wget https://repo1.maven.org/maven2/org/apache/flink/flink-s3-fs-hadoop/1.17.2/flink-s3-fs-hadoop-1.17.2.jar
RUN wget https://repo.maven.apache.org/maven2/org/apache/hudi/hudi-flink1.17-bundle/1.0.2/hudi-flink1.17-bundle-1.0.2.jar

# Below commands are used to support hudi to work with gs
RUN wget https://repo1.maven.org/maven2/org/apache/flink/flink-gs-fs-hadoop/1.17.2/flink-gs-fs-hadoop-1.17.2.jar
RUN wget https://repo1.maven.org/maven2/com/google/cloud/bigdataoss/gcs-connector/hadoop3-2.2.11/gcs-connector-hadoop3-2.2.11-shaded.jar  # Use hadoop2 if running with Hadoop2 image
RUN mv flink-gs-fs-hadoop-1.17.2.jar $FLINK_HOME/lib
RUN mv gcs-connector-hadoop3-2.2.11-shaded.jar $FLINK_HOME/lib


RUN mv flink-shaded-hadoop-2-uber-2.8.3-10.0.jar $FLINK_HOME/lib
RUN mv flink-s3-fs-hadoop-1.17.2.jar $FLINK_HOME/lib
RUN mv hudi-flink1.17-bundle-1.0.2.jar $FLINK_HOME/lib
COPY pipeline/hudi-connector/target/hudi-connector-1.0.0.jar $FLINK_HOME/lib/


# # Lakehouse connector image build
# FROM sanketikahub/flink:1.17.2-scala_2.12-java11 AS lakehouse-connector

# USER flink

# # Download and move required jars into Flink lib
# RUN wget https://repo1.maven.org/maven2/org/apache/flink/flink-shaded-hadoop-2-uber/2.8.3-10.0/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar -P /tmp && \
#     wget https://repo1.maven.org/maven2/org/apache/flink/flink-s3-fs-hadoop/1.17.2/flink-s3-fs-hadoop-1.17.2.jar -P /tmp && \
#     wget https://repo.maven.apache.org/maven2/org/apache/hudi/hudi-flink1.17-bundle/1.0.2/hudi-flink1.17-bundle-1.0.2.jar -P /tmp && \
#     mv /tmp/*.jar $FLINK_HOME/lib/

# # Copy the local hudi connector jar into the lib directory
# # IMPORTANT: You must build this Docker image from the root of your project so that this path is available
# COPY pipeline/hudi-connector/target/hudi-connector-1.0.0.jar $FLINK_HOME/lib/

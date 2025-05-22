FROM obsrv/apache-flink:2.0.0-scala_2.12
USER flink
RUN mkdir $FLINK_HOME/plugins/s3-fs-presto
RUN mkdir $FLINK_HOME/plugins/gs-fs-hadoop
RUN wget -nv -O flink-streaming-scala_2.12-2.0.0.jar "https://repo1.maven.org/maven2/org/apache/flink/flink-streaming-scala_2.12/2.0.0/flink-streaming-scala_2.12-2.0.0.jar"; \ 
    mv flink-streaming-scala_2.12-2.0.0.jar $FLINK_HOME/lib/
# COPY flink-shaded-hadoop2-uber-2.8.3-1.8.3.jar $FLINK_HOME/lib/
# COPY flink-s3-fs-hadoop-2.0.0.jar $FLINK_HOME/lib/
RUN wget -nv -O flink-azure-fs-hadoop-2.0.0.jar "https://repo1.maven.org/maven2/org/apache/flink/flink-azure-fs-hadoop/2.0.0/flink-azure-fs-hadoop-2.0.0.jar"; \ 
    mv flink-azure-fs-hadoop-2.0.0.jar $FLINK_HOME/lib/
RUN wget -nv -O flink-s3-fs-presto-2.0.0.jar "https://repo1.maven.org/maven2/org/apache/flink/flink-s3-fs-presto/2.0.0/flink-s3-fs-presto-2.0.0.jar"; \ 
    mv flink-s3-fs-presto-2.0.0.jar $FLINK_HOME/plugins/s3-fs-presto
RUN wget -nv -O flink-gs-fs-hadoop-2.0.0.jar "https://repo1.maven.org/maven2/org/apache/flink/flink-gs-fs-hadoop/2.0.0/flink-gs-fs-hadoop-2.0.0.jar"; \ 
    mv flink-gs-fs-hadoop-2.0.0.jar $FLINK_HOME/plugins/gs-fs-hadoop

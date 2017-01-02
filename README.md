Recommender
===========

José Ignacio Rojo Rivero

## Build Spark for BLAS/LAPACK support

BLAS and LAPACK are highly optimized computation frameworks, used by MLib.

In order to build Spark 2.1.0 with support for this, issue the following build command:

```bash
export MAVEN_OPTS="-Xmx2g -XX:ReservedCodeCacheSize=512m"
./build/mvn -Pnetlib-lgpl -Pyarn -Phadoop-2.7 -Dhadoop.version=2.7.3 -DskipTests clean package
```

## Launch local YARN cluster

Start HDFS and YARN locally

```bash
$HADOOP_HOME/bin/hadoop namenode -format
$HADOOP_HOME/sbin/start-dfs.sh
$HADOOP_HOME/sbin/start-yarn.sh
```

Put data into HDFS

```bash
$HADOOP_HOME/bin/hdfs dfs -mkdir /user
$HADOOP_HOME/bin/hdfs dfs -mkdir /user/josi
$HADOOP_HOME/bin/hdfs dfs -put data /user/josi
```

## Launch Spark job into YARN

```bash
USER_ID=2093760
$SPARK_HOME/bin/spark-submit \
    --master yarn \
    --deploy-mode cluster \
    --driver-memory 6g \
    --executor-memory 6g \
    --executor-cores 2 \
    --queue default \
    target/scala-2.11/spark-recommender_2.11-1.0.jar \
    $USER_ID
```

## Stop local YARN cluster

```bash
$HADOOP_HOME/sbin/stop-yarn.sh
$HADOOP_HOME/sbin/stop-dfs.sh
```

## References

1. [https://github.com/sryza/aas/tree/master/ch03-recommender](https://github.com/sryza/aas/tree/master/ch03-recommender)
2. [http://nordicapis.com/building-a-rest-api-in-java-scala-using-play-framework-2-part-1/](http://nordicapis.com/building-a-rest-api-in-java-scala-using-play-framework-2-part-1/)
3. [http://henningpetersen.com/post/22/running-apache-spark-jobs-from-applications](http://henningpetersen.com/post/22/running-apache-spark-jobs-from-applications)
4. [http://hadoop.apache.org/docs/r2.7.3/hadoop-project-dist/hadoop-common/SingleCluster.html#YARN_on_Single_Node]()http://hadoop.apache.org/docs/r2.7.3/hadoop-project-dist/hadoop-common/SingleCluster.html#YARN_on_Single_Node
5. [http://spark.apache.org/docs/latest/mllib-collaborative-filtering.html](http://spark.apache.org/docs/latest/mllib-collaborative-filtering.html)
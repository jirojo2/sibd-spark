# Recommender

José Ignacio Rojo Rivero

## Build Spark for BLAS/LAPACK support

BLAS and LAPACK are highly optimized computation frameworks, used by MLib.

In order to build Spark 2.1.0 with support for this, issue the following build command:

```bash
export MAVEN_OPTS="-Xmx2g -XX:ReservedCodeCacheSize=512m"
./build/mvn -Pnetlib-lgpl -Pyarn -Phadoop-2.7 -Dhadoop.version=2.7.3 -DskipTests clean package
```

Or add netlib dependency:

```bash
libraryDependencies += "com.github.fommil.netlib" % "all" % "1.1.2"
```

And finally, install openblas lib for your platform.

```bash
sudo apt-get install libopenblas-dev
```

## Launch local Spark job

Execute the steps as in Launch local YARN cluster, but it is not necesary to start yarn.
Then execute the following command, with master local[*]

```bash
$SPARK_HOME/bin/spark-submit \
    --master 'local[*]' \
    --driver-memory 3g \
    target/scala-2.11/spark-recommender_2.11-1.0.jar \
    2093760
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
sudo $HADOOP_HOME/bin/hdfs dfs -mkdir -p /user/josi
sudo $HADOOP_HOME/bin/hdfs dfs -chown josi /user/josi
$HADOOP_HOME/bin/hdfs dfs -put data /user/josi
```

## Launch Spark job into YARN

```bash
$SPARK_HOME/bin/spark-submit \
    --master yarn \
    --deploy-mode cluster \
    --driver-memory 3g \
    --executor-memory 2g \
    --executor-cores 2 \
    --queue default \
    target/scala-2.11/spark-recommender_2.11-1.0.jar \
    2093760
```

## Stop local YARN cluster

```bash
$HADOOP_HOME/sbin/stop-yarn.sh
$HADOOP_HOME/sbin/stop-dfs.sh
```

## Docker Swarm example

Assuming we have docker engine in this node, and we want to provision a swarm cluster:

```bash
docker run swarm create
# copy generated token (UUID)
```

Now we provision 3 vms with docker and a virtualbox driver
(change for other provision methods or virtualization technologies)

```bash
docker-machine create -d virtualbox --virtualbox-memory 4096 --swarm --swarm-master --swarm-discovery token://$SWARM_CLUSTER_TOKEN swarm-manager
docker-machine create -d virtualbox --virtualbox-memory 4096 --swarm --swarm-discovery token://$SWARM_CLUSTER_TOKEN swarm-node-01
docker-machine create -d virtualbox --virtualbox-memory 4096 --swarm --swarm-discovery token://$SWARM_CLUSTER_TOKEN swarm-node-02
```

With KVM it would be like this:

Install kvm driver for machine:

```bash
curl -L https://github.com/dhiltgen/docker-machine-kvm/releases/download/v0.7.0/docker-machine-driver-kvm > /usr/local/bin/docker-machine-driver-kvm
chmod +x /usr/local/bin/docker-machine-driver-kvm
```

Provision nodes:

```bash
docker-machine create -d kvm --kvm-memory 4096 --kvm-cpu-count 2 --swarm --swarm-master --swarm-discovery token://$SWARM_CLUSTER_TOKEN swarm-manager
docker-machine create -d kvm --kvm-memory 4096 --kvm-cpu-count 2 --swarm --swarm-discovery token://$SWARM_CLUSTER_TOKEN swarm-node-01
docker-machine create -d kvm --kvm-memory 4096 --kvm-cpu-count 2 --swarm --swarm-discovery token://$SWARM_CLUSTER_TOKEN swarm-node-02
```

In order to connect to a node of the cluster:

```bash
eval "$(docker-machine env --swarm swarm-manager)"
```

More info in [https://docs.docker.com/swarm/provision-with-machine/](https://docs.docker.com/swarm/provision-with-machine/)

### Setup multi-host network

Install consult in `swarm-keystore`:

```bash
docker-machine create -d kvm swarm-keystore
eval $(docker-machine env swarm-keystore)
docker run -d -p "8500:8500" -h "consul" progrium/consul -server -bootstrap
```

Create swarm cluster:

```bash
docker-machine create \
    -d kvm \
    --swarm --swarm-master \
    --swarm-discovery="consul://$(docker-machine ip swarm-keystore):8500" \
    --engine-opt="cluster-store=consul://$(docker-machine ip swarm-keystore):8500" \
    --engine-opt="cluster-advertise=eth1:2376" \
    swarm-manager

docker-machine create \
    -d kvm --kvm-memory 4096 --kvm-cpu-count 2 \
    --swarm \
    --swarm-discovery="consul://$(docker-machine ip swarm-keystore):8500" \
    --engine-opt="cluster-store=consul://$(docker-machine ip swarm-keystore):8500" \
    --engine-opt="cluster-advertise=eth1:2376" \
    swarm-node-01

docker-machine create \
    -d kvm --kvm-memory 4096 --kvm-cpu-count 2 \
    --swarm \
    --swarm-discovery="consul://$(docker-machine ip swarm-keystore):8500" \
    --engine-opt="cluster-store=consul://$(docker-machine ip swarm-keystore):8500" \
    --engine-opt="cluster-advertise=eth1:2376" \
    swarm-node-02
```

HDFS config file for Spark in host (file hdfs-site.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
    <property>
        <name>dfs.replication</name>
        <value>1</value>
    </property>
    <property>
        <name>dfs.client.use.datanode.hostname</name>
        <value>true</value>
    </property>
    <property>
        <name>dfs.datanode.use.datanode.hostname</name>
        <value>true</value>
    </property>
</configuration>
```

## References

1. [https://github.com/sryza/aas/tree/master/ch03-recommender](https://github.com/sryza/aas/tree/master/ch03-recommender)
2. [http://nordicapis.com/building-a-rest-api-in-java-scala-using-play-framework-2-part-1/](http://nordicapis.com/building-a-rest-api-in-java-scala-using-play-framework-2-part-1/)
3. [http://henningpetersen.com/post/22/running-apache-spark-jobs-from-applications](http://henningpetersen.com/post/22/running-apache-spark-jobs-from-applications)
4. [http://hadoop.apache.org/docs/r2.7.3/hadoop-project-dist/hadoop-common/SingleCluster.html#YARN_on_Single_Node](http://hadoop.apache.org/docs/r2.7.3/hadoop-project-dist/hadoop-common/SingleCluster.html#YARN_on_Single_Node)
5. [http://spark.apache.org/docs/latest/mllib-collaborative-filtering.html](http://spark.apache.org/docs/latest/mllib-collaborative-filtering.html)
6. [https://docs.docker.com/swarm/provision-with-machine/](https://docs.docker.com/swarm/provision-with-machine/)

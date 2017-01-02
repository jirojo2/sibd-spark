FROM openjdk:8-jdk-alpine

# passwordless ssh
RUN apk update && apk add openssh bash curl; \
    ssh-keygen -q -N "" -t dsa -f /etc/ssh/ssh_host_dsa_key; \
    ssh-keygen -q -N "" -t rsa -f /etc/ssh/ssh_host_rsa_key; \
    ssh-keygen -q -N "" -t rsa -f /root/.ssh/id_rsa; \
    cp /root/.ssh/id_rsa.pub /root/.ssh/authorized_keys

# hadoop
RUN curl -s http://www.eu.apache.org/dist/hadoop/common/hadoop-2.7.3/hadoop-2.7.3.tar.gz | tar -xz -C /usr/local/; \
    cd /usr/local && ln -s ./hadoop-2.7.3 hadoop

ENV HADOOP_PREFIX /usr/local/hadoop
ENV HADOOP_COMMON_HOME $HADOOP_PREFIX
ENV HADOOP_HDFS_HOME $HADOOP_PREFIX
ENV HADOOP_MAPRED_HOME $HADOOP_PREFIX
ENV HADOOP_YARN_HOME $HADOOP_PREFIX
ENV HADOOP_CONF_DIR $HADOOP_PREFIX/etc/hadoop
ENV YARN_CONF_DIR $HADOOP_PREFIX/etc/hadoop

# pseudo distributed
ADD etc/hadoop/core-site.xml $HADOOP_CONF_DIR/core-site.xml
ADD etc/hadoop/hdfs-site.xml $HADOOP_CONF_DIR/hdfs-site.xml
ADD etc/hadoop/mapred-site.xml $HADOOP_CONF_DIR/mapred-site.xml
ADD etc/hadoop/yarn-site.xml $HADOOP_CONF_DIR/yarn-site.xml

RUN $HADOOP_PREFIX/bin/hdfs namenode -format

# Hdfs ports
EXPOSE 50010 50020 50070 50075 50090
# Mapred ports
EXPOSE 19888
#Yarn ports
EXPOSE 8030 8031 8032 8033 8040 8042 8088
#Other ports
EXPOSE 49707 2122

# TODO: bootstrap script (start-dfs && start-yarn && sleep)
ADD bootstrap.sh /etc/bootstrap.sh
RUN chown root:root /etc/bootstrap.sh; \
    chmod 700 /etc/bootstrap.sh
CMD ["/etc/bootstrap.sh", "-d"]

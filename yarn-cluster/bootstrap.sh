#!/bin/bash

service ssh start

$HADOOP_PREFIX/sbin/start-dfs.sh

if [[ $1 = "-namenode" || $2 = "-namenode" ]]; then
  $HADOOP_PREFIX/sbin/start-yarn.sh
fi

if [[ $1 = "-d" || $2 = "-d" ]]; then
  while true; do sleep 1000; done
fi

if [[ $1 = "-bash" || $2 = "-bash" ]]; then
  /bin/bash
fi

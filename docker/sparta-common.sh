#!/bin/bash

function loadVariables() {
 source "${VARIABLES}"
 source "${SYSTEM_VARIABLES}"
}

function initSpark() {
  if [[ ! -v SPARK_HOME ]]; then
    SPARK_HOME="/opt/sds/spark"
  fi
  echo "" >> ${VARIABLES}
  echo "export SPARK_HOME=${SPARK_HOME}" >> ${VARIABLES}
  echo "" >> ${SYSTEM_VARIABLES}
  echo "export SPARK_HOME=${SPARK_HOME}" >> ${SYSTEM_VARIABLES}
}

function initHdfs() {
  if [[ -v HDFS_USER_NAME ]]; then
    echo "" >> ${SYSTEM_VARIABLES}
    echo "export HADOOP_USER_NAME=${HDFS_USER_NAME}" >> ${SYSTEM_VARIABLES}
  fi

  if [[ ! -v CORE_SITE_FROM_URI ]]; then
   CORE_SITE_FROM_URI="false"
  fi
  if [ $CORE_SITE_FROM_URI == "true" ] && [ -v DEFAULT_FS ] && [ ${#DEFAULT_FS} != 0 ]; then
    if [ ! -v HADOOP_CONF_DIR ] && [ HADOOP_CONF_DIR != 0 ]; then
      HADOOP_CONF_DIR=/opt/sds/hadoop/conf
    fi
    sed -i "s|.*sparta.hdfs.hdfsMaster.*|sparta.hdfs.hdfsMaster = \""${DEFAULT_FS}"\"|" ${SPARTA_CONF_FILE}
    source hdfs_utils.sh
    generate_core-site-from-uri
  fi

  if [[ ! -v CORE_SITE_FROM_DFS ]]; then
   CORE_SITE_FROM_DFS="false"
  fi
  if [ $CORE_SITE_FROM_DFS == "true" ] && [ -v DEFAULT_FS ] && [ ${#DEFAULT_FS} != 0 ]; then
    if [ ! -v HADOOP_CONF_DIR ] && [ HADOOP_CONF_DIR != 0 ]; then
      HADOOP_CONF_DIR=/opt/sds/hadoop/conf
    fi
    sed -i "s|.*sparta.hdfs.hdfsMaster.*|sparta.hdfs.hdfsMaster = \""${DEFAULT_FS}"\"|" ${SPARTA_CONF_FILE}
    source hdfs_utils.sh
    generate_core-site-from-fs
  fi
}

function logLevelOptions() {
  if [[ ! -v SERVICE_LOG_LEVEL ]]; then
    SERVICE_LOG_LEVEL="ERROR"
  fi
  sed -i "s|<root level.*|<root level = \""${SERVICE_LOG_LEVEL}"\">|" ${LOG_CONFIG_FILE}

  if [[ ! -v SPARTA_LOG_LEVEL ]]; then
    SPARTA_LOG_LEVEL="INFO"
  fi
  sed -i "s|com.stratio.sparta.*|com.stratio.sparta\" level= \""${SPARTA_LOG_LEVEL}"\"/>|" ${LOG_CONFIG_FILE}

  if [[ ! -v SPARK_LOG_LEVEL ]]; then
    SPARK_LOG_LEVEL="ERROR"
  fi
  sed -i "s|org.apache.spark.*|org.apache.spark\" level= \""${SPARK_LOG_LEVEL}"\"/>|" ${LOG_CONFIG_FILE}

  if [[ ! -v HADOOP_LOG_LEVEL ]]; then
    HADOOP_LOG_LEVEL="ERROR"
  fi
  sed -i "s|org.apache.hadoop.*|org.apache.hadoop\" level= \""${HADOOP_LOG_LEVEL}"\"/>|" ${LOG_CONFIG_FILE}
}

function logLevelToStdout() {
  SERVICE_LOG_APPENDER="STDOUT"
  export SPARTA_OPTS="$SPARTA_OPTS -Dconfig.file=$SPARTA_CONF_FILE"
  sed -i "s|<appender-ref ref.*|<appender-ref ref= \""${SERVICE_LOG_APPENDER}"\" />|" ${LOG_CONFIG_FILE}
}

function logLevelToFile() {
  SERVICE_LOG_APPENDER="FILE"
     sed -i "s|<appender-ref ref.*|<appender-ref ref= \""${SERVICE_LOG_APPENDER}"\" />|" ${LOG_CONFIG_FILE}
}
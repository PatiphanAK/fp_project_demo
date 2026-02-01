#!/bin/bash
set -e

SPARK_WORKLOAD=$1

if [ -z "$SPARK_WORKLOAD" ]; then
  echo "Error: SPARK_WORKLOAD not specified"
  echo "Usage: entrypoint.sh [master|worker|history]"
  exit 1
fi

export SPARK_HOME=${SPARK_HOME:-/opt/spark}
export PATH="${SPARK_HOME}/bin:${SPARK_HOME}/sbin:${PATH}"

EVENT_LOG_DIR=${SPARK_EVENTLOG_DIR:-/opt/spark/spark-events}

echo "Starting Spark in $SPARK_WORKLOAD mode..."
echo "SPARK_HOME: ${SPARK_HOME}"
echo "EVENT_LOG_DIR: ${EVENT_LOG_DIR}"

if [ "$SPARK_WORKLOAD" == "master" ]; then
  exec ${SPARK_HOME}/sbin/start-master.sh -p 7077

elif [ "$SPARK_WORKLOAD" == "worker" ]; then
  exec ${SPARK_HOME}/sbin/start-worker.sh spark://spark-master:7077

elif [ "$SPARK_WORKLOAD" == "history" ]; then
  mkdir -p ${EVENT_LOG_DIR}

  export SPARK_HISTORY_OPTS="
    -Dspark.history.fs.logDirectory=file:${EVENT_LOG_DIR}
  "

  exec ${SPARK_HOME}/sbin/start-history-server.sh

else
  echo "Error: Unknown workload '$SPARK_WORKLOAD'"
  exit 1
fi

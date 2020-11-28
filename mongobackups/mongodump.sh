#!/bin/bash

# Exit when a command fails
set -o errexit

# Trace what gets executed
set -o xtrace

TIMENAME=`date +%y%m.%d.0`

if [ -n "$1" ];
then
    TIMENAME=$1
fi

echo ${TIMENAME}

mongodump --port=26459 --out=/opt/mongobackups/${TIMENAME} --gzip


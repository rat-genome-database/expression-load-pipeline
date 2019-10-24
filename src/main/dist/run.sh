#!/usr/bin/env bash
# shell script to run Expression Load pipeline
. /etc/profile

APPNAME=expressionLoadPipeline
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/${APPNAME}.jar "$@" > $APPDIR/run.log 2>&1

mailx -s "[$SERVER] Expression Load pipeline ok" hsnalabolu@mcw.edu < $APPDIR/logs/status.log

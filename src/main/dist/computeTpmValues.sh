#!/usr/bin/env bash
# compute values in TPM_VALUE column  of GENE_EXPRESSION_VALUES table
. /etc/profile

APPNAME=expressionLoadPipeline
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db.xml \
    -Dlog4j.configurationFile=file://$APPDIR/properties/log4j2.xml \
    -jar lib/${APPNAME}.jar --updateTpmValues "$@" | tee $APPDIR/run.log 2>&1

mailx -s "[$SERVER] TPM value computation complete!" mtutaj@mcw.edu < $APPDIR/logs/status.log

#!/bin/bash

instanceid=`curl -s http://169.254.169.254/latest/meta-data/instance-id`
region=`curl -s http://169.254.169.254/latest/meta-data/placement/availability-zone |sed 's/.$//'`

apptag=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=App" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`
appdir=/$apptag-1.0-SNAPSHOT
stacktag=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=Stack" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`
stagetag=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=Stage" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`

loggingrole=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=LoggingRole" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`
loggingstream=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=LoggingStream" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`

log_group_name="$stacktag-$apptag-$stagetag"

filter_pattern="[..., type=REQUEST, remoteAddr, method, uri, took=took, time, ms=ms, and=and, returned=returned, statusCode]"

cat > awslogs.conf <<__END__
[general]
state_file = /var/awslogs/state/agent-state
[$apptag-$stagetag]
datetime_format = %Y-%m-%d %H:%M:%S
file = /$apptag-1.0-SNAPSHOT/logs/application.log
buffer_duration = 5000
log_stream_name = $apptag-$stagetag-$instanceid
initial_position = start_of_file
log_group_name = $log_group_name

[$apptag-$stagetag-GC]
datetime_format = %Y-%m-%d %H:%M:%S
file = /$apptag-1.0-SNAPSHOT/gc.log.0
buffer_duration = 5000
log_stream_name = $apptag-$stagetag-$instanceid-GC
initial_position = start_of_file
log_group_name = $stacktag-$apptag-$stagetag-GC
__END__

wget https://s3.amazonaws.com/aws-cloudwatch/downloads/latest/awslogs-agent-setup.py
python ./awslogs-agent-setup.py -n -r $region -c awslogs.conf

aws s3 cp s3://mobile-notifications-dist/$stagetag/$stacktag.properties /etc/gu/$apptag.properties

s3_app_conf=`mktemp` || exit 1
app_conf="${appdir}/conf/application.conf"
sudo -u ${apptag} sh -c "touch ${app_conf}"
aws s3 cp s3://mobile-notifications-dist/${stagetag}/application.conf ${s3_app_conf}
chown ${apptag}:${apptag} ${s3_app_conf}
sudo -u ${apptag} sh -c "cat ${app_conf} ${s3_app_conf} > ${appdir}/conf/application.conf.all"
sudo -u ${apptag} sh -c "rm ${app_conf} && mv ${appdir}/conf/application.conf.all ${app_conf}"

cp /$apptag-1.0-SNAPSHOT/conf/init.conf /etc/init/report.conf

start report

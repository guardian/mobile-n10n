#!/bin/bash

instanceid=`curl -s http://169.254.169.254/latest/meta-data/instance-id`
region=`curl -s http://169.254.169.254/latest/meta-data/placement/availability-zone |sed 's/.$//'`

apptag=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=App" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`
stacktag=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=Stack" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`
stagetag=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=Stage" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`

loggingrole=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=LoggingRole" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`
loggingstream=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=LoggingStream" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`
log_group_name="$stacktag-$apptag-$stagetag"

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
__END__

wget https://s3.amazonaws.com/aws-cloudwatch/downloads/latest/awslogs-agent-setup.py
python ./awslogs-agent-setup.py -n -r $region -c awslogs.conf

aws s3 cp s3://mobile-notifications-dist/$stagetag/$stacktag.properties /etc/init/backup.conf

adduser --home /$apptag-1.0-SNAPSHOT --disabled-password --gecos \"\" user
chown -R user /$apptag-1.0-SNAPSHOT
sudo -u user /backup-1.0-SNAPSHOT/bin/backup /backup-1.0-SNAPSHOT
#!/bin/bash

instanceid=`curl -s http://169.254.169.254/latest/meta-data/instance-id`
region=`curl -s http://169.254.169.254/latest/meta-data/placement/availability-zone |sed 's/.$//'`

apptag=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=App" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`
stacktag=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=Stack" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`
stagetag=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=Stage" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`

aws s3 cp s3://mobile-notifications-dist/$stagetag/$stacktag.properties /etc/init/backup.conf

/backup-1.0-SNAPSHOT/bin/backup
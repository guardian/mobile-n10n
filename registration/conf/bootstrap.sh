#!/bin/bash

instanceid=`curl -s http://169.254.169.254/latest/meta-data/instance-id`
region=`curl -s http://169.254.169.254/latest/meta-data/placement/availability-zone |sed 's/.$//'`

apptag=`aws ec2 describe-tags --filters "Name=resource-id,Values=$instanceid" "Name=resource-type,Values=instance" "Name=key,Values=App" --region $region | grep -oP "(?<=\"Value\": \")[^\"]+"`
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
file = /$apptag-1.0-SNAPSHOT/logs/$apptag.log
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

cp /$apptag-1.0-SNAPSHOT/conf/init.conf /etc/init/registration.conf

start registration

tmpfile=/tmp/filters.$$

aws logs describe-subscription-filters \
    --log-group-name $stacktag-$apptag-$stagetag \
    --region eu-west-1 \
    --filter-name-prefix "cwl-es-" > $tmpfile

current_name=$(grep filterName $tmpfile | awk -F \" '{ print $4 };')
current_pattern=$(grep filterPattern $tmpfile | awk -F \" '{ print $4 };')
current_role=$(grep roleArn $tmpfile | awk -F \" '{ print $4 };')
current_dest=$(grep destinationArn $tmpfile | awk -F \" '{ print $4 };')

rm $tmpfile

if [ "$current_pattern" != "$filter_pattern" ] || \
    [ "$current_role" != "$loggingrole" ] || \
    [ "$current_dest" != "$loggingstream" ]; then
    if [ "$current_name" != "" ]; then
        aws logs delete-subscription-filter \
            --log-group-name $log_group_name \
            --filter-name $current_name \
            --region eu-west-1
    fi

    aws logs put-subscription-filter \
        --log-group-name $log_group_name \
        --filter-name cwl-es-$log_group_name \
        --filter-pattern "$filter_pattern" \
        --destination-arn "$loggingstream" \
        --role-arn "$loggingrole" \
        --region eu-west-1
fi
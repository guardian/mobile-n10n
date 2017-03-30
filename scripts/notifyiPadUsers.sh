#!/bin/sh

# make a log file. Sept 2016 set to append if exists rather than overwrite

GNLMakeLog()
{
mkdir -p /Library/Logs/iPadLaunches/
chmod 775 /Library/Logs/iPadLaunches/
touch /Library/Logs/iPadLaunches/${todaysDate}.log
echo "------------------------------------------------" >> /Library/Logs/iPadLaunches/${todaysDate}.log
}

# logging

GNLAppendLog()
{
echo `date "+%d/%m/%y %H:%M:%S"`"	$1">>/Library/Logs/iPadLaunches/${todaysDate}.log
echo $1
}

# Date format should be YYYYMMDD
# todaysDate is just used for logging. checkDate is used in the check

todaysDate=`date +"%Y%m%d"`
logFile="/Library/Logs/iPadLaunches/${todaysDate}.log"
successLog="Push notification for GNM sent successfully"

# Sept 2016 if log exists AND contains a successful push request, don't bother proceed.

if [ -f "$logFile" ] && grep -F "$successLog" "$logFile" 
then
	GNLAppendLog "------------------------------------------------"
	GNLAppendLog "A notification has already been sent for $todaysDate. Goodnight"
	exit 0
elif [ -f "$logFile" ] 
then
	echo "------------------------------------------------" >> /Library/Logs/iPadLaunches/${todaysDate}.log
	GNLAppendLog "Trying again..."
fi

# START CHECKING

# initialise log for today (it may be already present if we're on round two)

GNLMakeLog

# Get the date value to check, using string provided to the script, or today's date 

if [ $1 ]; then
  checkDate=$1
  GNLAppendLog "Checking the live json for date provided to me: $checkDate."
else
  checkDate=`date +"%Y%m%d"`
  GNLAppendLog "Checking the live json for today's date: $checkDate."
fi

# Grep for the above date string in the three regions' index json files. 
# NB this will get false positives from the releaseDate element if we were 
# ever to relaunch an old issue between midnight and 3am.

UK=`curl --silent https://s3-eu-west-1.amazonaws.com/ipad-packager-dist/PROD/ipad_editions/dstyhsjhdfiomqzojdsw/uk/index.v2.json | grep $checkDate  | wc -l`
USA=`curl --silent https://s3-eu-west-1.amazonaws.com/ipad-packager-dist/PROD/ipad_editions/dstyhsjhdfiomqzojdsw/usa/index.v2.json | grep $checkDate | wc -l`
ROW=`curl --silent https://s3-eu-west-1.amazonaws.com/ipad-packager-dist/PROD/ipad_editions/dstyhsjhdfiomqzojdsw/row/index.v2.json | grep $checkDate | wc -l`

# If all three got a result, all's good. If not, abort.

if [ "$UK" -gt "0" -a "$USA" -gt "0" -a "$ROW" -gt "0" ]; then
  GNLAppendLog "The text $checkDate was found in the live json files on Amazon."
else
  GNLAppendLog "ERROR: date check failed. Either the date $checkDate wasn't present or my cURL didn't work."
  GNLAppendLog "Push notification request aborted."
  /usr/bin/mail -s  "Tablet push notifications error: $checkDate" luke.hoyland@theguardian.com < "$logFile"
  exit 1
fi

# If we're still going at this point, then the issue date appears in the index.v2.json files
# that are available to the client. So it's safe to request the push notifications.

GNLAppendLog "I'm going for it."

# GNM INTERNAL Notification

#/usr/bin/curl -X POST "https://notifications.guardianapis.com/push/newsstand?api-key=????????????????"
/usr/bin/curl -X POST "https://notifications.guardian.co.uk/newsstand/trigger?api-key=9fpnUKZXKHx3ymRMoey7"

curlExit=$?

if [ "$curlExit" -ne "0" ]; then
        GNLAppendLog "Problem sending GNM notification - cURL error: $curlExit"
else
        GNLAppendLog "$successLog. Goodnight"
fi



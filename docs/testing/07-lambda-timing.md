# Lambda Timings

We analysed lambda timings to try and understand how close we are to achieving our "90 in 2" target for breaking news notifications.

We consider notifications with a registration count greater than 200,000 to be of the magnitude of a breaking notification (where registration count is the number of users signed up to receive a notification of specific type, e.g. "breaking/uk").

We collected data for notifications with different ranges of registration count. From the data we collected we can see:
- For registration counts less than 200,000 we seem to hit our SLO
- When we meet our SLO we tend to complete sending all notifications within in about 1m30s
- For registration counts greater than 200,000 we never hit our SLO
- For registration counts greater than 200,000 we need to reduce our processing time by about 60%
- The harvester scales reasonably well with increasing registration count
- The sender lambdas appear to be our current bottleneck

| Registration Count     | % delivered within 2 mins | Time between notification request received and last sender finished (s) | Harvester Total Duration (s) | Senders Total Duration (s) |
|:-----------------------|:--------------------------|:------------------------------------------------------------------------|:-----------------------------|:---------------------------|
| r.c. > 200000          | 58                        | 230                                                                     | 51                           | 224                        |
| 100000 < r.c. > 200000 | 94                        | 94                                                                      | 35                           | 86                         |


## Registration Count > 200000

| Notification ID                      | DateTime Sent | Total Registration Count | % delivered within 2 mins | Time between notification request received and last sender finished (s) | Harvester Total Duration (s) | Senders Total Duration (s) |
|:-------------------------------------|:--------------|:-------------------------|:--------------------------|:------------------------------------------------------------------------|:-----------------------------|:---------------------------|
| 4bb17319-8ab6-4b44-a691-0b4978e8fba0 | 27/07 20:59   | 914411                   | 59.87                     | 234                                                                     | 38                           | 233                        |
| 53582044-7353-40fe-9543-5bbc50bc6bae | 27/07 15:50   | 910814                   | 46.36                     | 254                                                                     | 63                           | 245                        |
| a077af9a-64f9-4e5b-b386-d3cea55865ad | 27/07 13:41   | 908450                   | 58.91                     | 225                                                                     | 53                           | 234                        |
| db725a7b-66e9-4f9f-8880-6710866ab2c7 | 27/07 16:17   | 906683                   | 54.91                     | 214                                                                     | 42                           | 213                        |
| db725a7b-66e9-4f9f-8880-6710866ab2c7 | 27/07 18:05   | 292759                   | 67.48                     | 197                                                                     | 44                           | 196                        |
| 068ae268-73e1-49bd-b555-d7131d88491c | 28/07 12:34   | 566373                   | 57.11                     | 250                                                                     | 57                           | 207                        |
| 999dbcda-9adc-4e30-86a5-3312488ff0b2 | 28/07 08:19   | 913086                   | 58.84                     | 241                                                                     | 59                           | 240                        |

## 100000 < Registration Count > 200000

| Notification ID                      | DateTime Sent | Total Registration Count | % delivered within 2 mins | Time between notification request received and last sender finished (s) | Harvester Total Duration (s) | Senders Total Duration (s) |
|:-------------------------------------|:--------------|:-------------------------|:--------------------------|:------------------------------------------------------------------------|:-----------------------------|:---------------------------|
| 02e0374d-18fb-34b3-b159-ef8b74da2df9 | 27/07 05:34   | 162470                   | 93.86                     | 85                                                                      | 36                           | 70                         |
| 408647b7-7080-4b99-b34f-46b5d8322d99 | 27/07 22:27   | 176502                   | 96.04                     | 94                                                                      | 38                           | 93                         |
| 95cc4629-6bb5-4ca2-922e-25419bdc56d5 | 27/07 01:35   | 174940                   | 96.31                     | 105                                                                     | 31                           | 93                         |
| 5dccc8fa-331c-325b-acb6-3961e0c2711c | 28/07 05:29   | 161970                   | 93.66                     | 90                                                                      | 33                           | 76                         |
| 970c7285-e6f0-44b5-87ab-ffb3971fa422 | 28/07 01:25   | 174917                   | 96.51                     | 100                                                                     | 47                           | 85                         |
| 988136a1-2da6-4b5d-8193-707db4f1ce88 | 28/07 20:05   | 168902                   | 92.53                     | 97                                                                      | 32                           | 95                         |
| b1d7f99f-b5e7-4660-8d6b-e7bd51c09a36 | 28/07 06:09   | 175491                   | 90.96                     | 89                                                                      | 28                           | 87                         |

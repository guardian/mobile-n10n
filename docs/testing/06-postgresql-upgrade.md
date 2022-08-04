# Postgresql Database Upgrade

This document summarises the test result for database upgrade.  The corresponding background and research work can be found in [the document](../architecture/03-postgresql-upgrade.md).

## Results

A performance test was performed for each of the scenarios:
1. Before upgrade from Postgresql 10
2. Upgraded to Postgresql 14
3. Upgraded to Postgresql 13

Under each scenario, I sent four rounds of breaking news notification.

### Before upgrade from Postgresql 10

| # | Total harvester duration (s) | No. harvester invocations | Harvester error "marked as broken" |
| ----------- | ----------- | ----------- | ----------- |
| 1	| 32.41 | 159 | 0 |
| 2	| 12.36 | 159 | 0 |
| 3	| 11.97 | 159 | 0 |
| 4	| 11.2 | 159 | 0 |
| AVG | 16.985 | 159 | 0 |

### Postgresql 14

| # | Total harvester duration (s) | No. harvester invocations | Harvester error "marked as broken" |
| ----------- | ----------- | ----------- | ----------- |
| 1 | 28.85 | 162 | 0 |
| 2 | 15.6 | 159 | 0 |
| 3 | 10.41 | 159 | 0 |
| 4 | 13.08 | 159 | 0 |
| AVG | 16.985 | 159.75 | 0 |

### Postgresql 13

| # | Total harvester duration (s) | No. harvester invocations | Harvester error "marked as broken" |
| ----------- | ----------- | ----------- | ----------- |
1 | 33.41| 158 | 0 |
2 | 14.52| 158 | 0 |
3 | 17.94| 158 | 0 |
4 | 24.6 | 158 | 0 |
| AVG | 22.6175 | 158 | 0 |

## Conclusion







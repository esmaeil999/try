# Dukascopy Tick Exporter

Downloads historical tick data from Dukascopy's public data feed
and saves it as CSV — no JForex platform required.

## Quick Start (local)

```bash
mvn clean package
java -jar target/tick-exporter-1.0.0.jar EURUSD 2024-01-01 2024-01-02 ticks.csv

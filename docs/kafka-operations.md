# Kafka notification operations

The Member Service publishes community notice events to `community-notices.events` and lost-and-found events to `lost-found.events`. After three failed listener attempts, records are sent to the corresponding `.DLT` topic. The DLT payload is the original JSON event; its Kafka key remains the notice or report ID.

Inspect a dead-letter topic (replace `community-notices.events.DLT` with the desired topic):

```sh
docker exec -it emerald-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic community-notices.events.DLT --from-beginning
```

To replay selected records, copy the JSON payload from the inspection output and publish it to the original topic. Use the event's notice or report ID as the key. For example:

```sh
docker exec -i emerald-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic community-notices.events --property parse.key=true --property key.separator=:
```

Then enter `NOTICE_UUID:{...event JSON...}`. Consumers deduplicate by `(flow, eventId)`, so an event already committed to the database will not repeat notification effects. To intentionally reprocess one, remove its row from `kafka_processed_events` first, after reviewing the impact.

Micrometer exposes `kafka.messages.sent`, `kafka.messages.consumed`, `kafka.messages.failed`, and `kafka.outbox.waiting`, tagged by flow. Consumer failures are counted when sent to a DLT; duplicate deliveries are separately counted as `kafka.messages.duplicate`.

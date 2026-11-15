ALTER TABLE community_notice_outbox
  ADD COLUMN schema_version integer NOT NULL DEFAULT 1,
  ADD COLUMN event_timestamp timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN correlation_id varchar(128),
  ADD COLUMN claim_token uuid,
  ADD COLUMN claimed_until timestamptz;

ALTER TABLE lost_found_event_outbox
  ADD COLUMN schema_version integer NOT NULL DEFAULT 1,
  ADD COLUMN event_timestamp timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN correlation_id varchar(128),
  ADD COLUMN claim_token uuid,
  ADD COLUMN claimed_until timestamptz;

CREATE INDEX community_notice_outbox_claim_idx ON community_notice_outbox(claimed_until, created_at);
CREATE INDEX lost_found_outbox_claim_idx ON lost_found_event_outbox(claimed_until, created_at);

CREATE TABLE kafka_processed_events (
  flow varchar(80) NOT NULL,
  event_id uuid NOT NULL,
  processed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (flow, event_id)
);

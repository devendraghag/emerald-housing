CREATE TABLE community_notices (
  id uuid PRIMARY KEY,
  created_by uuid NOT NULL REFERENCES admin_users(id),
  title varchar(160) NOT NULL,
  message varchar(4000) NOT NULL,
  audience_scope varchar(20) NOT NULL CHECK (audience_scope IN ('ADMIN_ONLY','SOCIETY','FLATS')),
  status varchar(24) NOT NULL CHECK (status IN ('SUBMITTED','UNDER_REVIEW','PUBLISHED','REJECTED','CLOSED','EXPIRED')),
  reviewed_by uuid REFERENCES admin_users(id),
  reviewed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK ((audience_scope='FLATS') OR audience_scope IN ('ADMIN_ONLY','SOCIETY'))
);

CREATE TABLE community_notice_flats (
  notice_id uuid NOT NULL REFERENCES community_notices(id) ON DELETE CASCADE,
  flat_id uuid NOT NULL,
  PRIMARY KEY (notice_id, flat_id)
);
CREATE INDEX community_notice_flat_idx ON community_notice_flats(flat_id, notice_id);
CREATE INDEX community_notice_board_idx ON community_notices(status, audience_scope, created_at DESC);

CREATE TABLE community_notice_outbox (
  event_id uuid PRIMARY KEY,
  notice_id uuid NOT NULL,
  event_type varchar(80) NOT NULL,
  actor_id uuid NOT NULL,
  occurred_at timestamptz NOT NULL,
  attempts integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE community_notice_notifications (
  event_id uuid NOT NULL,
  recipient_id uuid NOT NULL REFERENCES admin_users(id),
  notice_id uuid NOT NULL REFERENCES community_notices(id),
  event_type varchar(80) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  read_at timestamptz,
  PRIMARY KEY(event_id, recipient_id)
);
CREATE INDEX community_notice_notifications_recipient_idx
  ON community_notice_notifications(recipient_id, created_at DESC);

INSERT INTO access_permissions(code, description) VALUES
 ('COMMUNITY_NOTICE_ADMIN','Review, publish, and target community notices'),
 ('COMMUNITY_NOTICE_CREATE','Submit a community notice for review'),
 ('COMMUNITY_NOTICE_BOARD','View published notices addressed to this account')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code, scope)
SELECT r.code, p.code, 'SOCIETY'
FROM access_roles r CROSS JOIN access_permissions p
WHERE r.enabled AND r.code IN ('ADMIN','SOCIETY_ADMIN') AND p.code='COMMUNITY_NOTICE_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code, scope)
SELECT r.code, p.code, 'SOCIETY'
FROM access_roles r CROSS JOIN access_permissions p
WHERE r.enabled AND r.code IN ('ADMIN','SOCIETY_ADMIN','COMMITTEE','RESIDENT','FLAT_OWNER','FAMILY_MEMBER','TENANT')
  AND p.code IN ('COMMUNITY_NOTICE_CREATE','COMMUNITY_NOTICE_BOARD')
ON CONFLICT DO NOTHING;

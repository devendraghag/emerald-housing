CREATE UNIQUE INDEX uq_booked_viewing_member_slot
    ON rental_operations(member_id, ((details->>'slot')::timestamptz))
    WHERE operation_type = 'VIEWING' AND status = 'BOOKED' AND member_id IS NOT NULL;

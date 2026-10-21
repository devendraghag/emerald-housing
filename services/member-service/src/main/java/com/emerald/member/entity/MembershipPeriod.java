package com.emerald.member.entity;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Predicate;

/** The date policy used to derive a resident's current flat access. */
public interface MembershipPeriod {
    LocalDate getStartDate();

    LocalDate getEndDate();

    /**
     * Only started, open memberships grant access. An explicitly recorded end date
     * removes current access even when that date is in the future.
     */
    default boolean isCurrentOn(LocalDate date) {
        Objects.requireNonNull(date, "Membership reference date is required");
        return hasStartedOn(date) && getEndDate() == null;
    }

    /** Builds a reusable stream predicate using the same current-access policy. */
    static Predicate<MembershipPeriod> currentOn(LocalDate date) {
        Objects.requireNonNull(date, "Membership reference date is required");
        return membership -> membership.isCurrentOn(date);
    }

    private boolean hasStartedOn(LocalDate date) {
        return !getStartDate().isAfter(date);
    }
}

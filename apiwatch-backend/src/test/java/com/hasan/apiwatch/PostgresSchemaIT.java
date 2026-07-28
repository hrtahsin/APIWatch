package com.hasan.apiwatch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresSchemaIT extends PostgresIntegrationTest {

    @Test
    void appliesEveryFlywayMigrationAndValidatesCriticalIndexes() {
        List<String> versions = jdbcTemplate.queryForList("""
                SELECT version
                FROM flyway_schema_history
                WHERE success = TRUE
                  AND type = 'SQL'
                ORDER BY installed_rank
                """, String.class);

        assertThat(versions)
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");

        String activeIncidentIndex = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = 'uq_incidents_one_active_per_service'
                """, String.class);
        assertThat(activeIncidentIndex)
                .contains("UNIQUE")
                .contains("WHERE")
                .contains("status");

        String notificationEventIndex = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = 'uq_notification_delivery_event_key'
                """, String.class);
        assertThat(notificationEventIndex).contains("UNIQUE").contains("event_key");
    }

    @Test
    void usesTimezoneAwareColumnsForClaimsAndMonitoringTimestamps() {
        List<String> timezoneColumns = jdbcTemplate.queryForList("""
                SELECT table_name || '.' || column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND data_type = 'timestamp with time zone'
                  AND (
                    (table_name = 'notification_deliveries'
                        AND column_name IN ('claimed_until', 'last_attempt_at'))
                    OR (table_name = 'monitoring_leases'
                        AND column_name IN ('leased_until', 'updated_at'))
                  )
                ORDER BY table_name, column_name
                """, String.class);

        assertThat(timezoneColumns).containsExactly(
                "monitoring_leases.leased_until",
                "monitoring_leases.updated_at",
                "notification_deliveries.claimed_until",
                "notification_deliveries.last_attempt_at"
        );
    }
}

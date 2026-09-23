package com.hic.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalTime;

@Data
@Entity
@Table(
        name = "timetable_day_rules",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_timetable_day_rules_day",
                columnNames = {"timetable_id", "day_of_week"}
        )
)
public class TimetableDayRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timetable_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Timetable timetable;

    /** ISO day number: Monday = 1, Sunday = 7. */
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "working_day", nullable = false)
    private Boolean workingDay = true;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "break_minutes", nullable = false)
    private Integer breakMinutes = 0;

    @Column(name = "allowed_late_minutes", nullable = false)
    private Integer allowedLateMinutes = 0;

    @Column(name = "allowed_early_leave_minutes", nullable = false)
    private Integer allowedEarlyLeaveMinutes = 0;
}

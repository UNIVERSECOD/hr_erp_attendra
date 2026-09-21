package com.hic.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "holiday_targets")
public class HolidayTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_id", nullable = false)
    private Long holidayId;

    @Column(name = "scope_type", nullable = false)
    private String scopeType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;
}

package com.hic.service;

import com.hic.exception.BadRequestException;
import com.hic.model.Timetable;
import com.hic.model.TimetableDayRule;
import com.hic.repository.TimetableRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimetableServiceTest {

    @Mock
    private TimetableRepository timetableRepository;

    @InjectMocks
    private TimetableService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_savesSevenRulesAndKeepsLegacyFieldsAligned() {
        Timetable timetable = baseTimetable();
        timetable.setDayRules(weekRules());
        when(timetableRepository.save(any(Timetable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Timetable saved = service.create(timetable);

        assertThat(saved.getTenantId()).isEqualTo(7L);
        assertThat(saved.getDayRules()).hasSize(7);
        assertThat(saved.getDayRules()).allSatisfy(rule -> assertThat(rule.getTimetable()).isSameAs(saved));
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(saved.getAllowedLateMinutes()).isEqualTo(30);
        assertThat(saved.getAllowedEarlyLeaveMinutes()).isEqualTo(15);
    }

    @Test
    void create_rejectsDuplicateOrMissingWeekdays() {
        Timetable timetable = baseTimetable();
        List<TimetableDayRule> rules = weekRules();
        rules.get(6).setDayOfWeek(6);
        timetable.setDayRules(rules);

        assertThatThrownBy(() -> service.create(timetable))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("unique ISO days");
    }

    private Timetable baseTimetable() {
        Timetable timetable = new Timetable();
        timetable.setName("Standart");
        timetable.setShiftType("STANDARD");
        timetable.setStartTime(LocalTime.of(9, 0));
        timetable.setEndTime(LocalTime.of(18, 0));
        return timetable;
    }

    private List<TimetableDayRule> weekRules() {
        List<TimetableDayRule> rules = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            TimetableDayRule rule = new TimetableDayRule();
            rule.setDayOfWeek(day);
            rule.setWorkingDay(day <= 6);
            rule.setStartTime(LocalTime.of(8, 0));
            rule.setEndTime(day == 6 ? LocalTime.of(16, 0) : LocalTime.of(17, 0));
            rule.setBreakMinutes(60);
            rule.setAllowedLateMinutes(30);
            rule.setAllowedEarlyLeaveMinutes(15);
            rules.add(rule);
        }
        return rules;
    }
}

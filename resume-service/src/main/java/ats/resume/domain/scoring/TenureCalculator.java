package ats.resume.domain.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Объединение периодов работы без двойного учёта: 2018–2021 и 2020–2023 дают 5 лет. */
public final class TenureCalculator {

    private TenureCalculator() {}

    public static double years(List<Period> periods) {
        if (periods == null || periods.isEmpty()) {
            return 0;
        }
        List<Period> sorted = new ArrayList<>(periods);
        sorted.sort(Comparator.comparingInt(Period::startYear).thenComparingInt(Period::endYear));
        int total = 0;
        Period current = sorted.getFirst();
        for (int index = 1; index < sorted.size(); index++) {
            Period next = sorted.get(index);
            if (next.startYear() <= current.endYear()) {
                current =
                        new Period(
                                current.startYear(), Math.max(current.endYear(), next.endYear()));
            } else {
                total += current.endYear() - current.startYear();
                current = next;
            }
        }
        total += current.endYear() - current.startYear();
        return total;
    }

    public record Period(int startYear, int endYear) {

        public Period {
            if (startYear < 0 || endYear < startYear) {
                throw new IllegalArgumentException("invalid period");
            }
        }
    }
}

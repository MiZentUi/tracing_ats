package ats.resume.domain.privacy;

/** Нормализованные факты об образовании. Не содержат названия вуза и года окончания. */
public record EducationFacts(String level, String degree, String specialty, Double years) {

    public static EducationFacts empty() {
        return new EducationFacts(null, null, null, null);
    }
}

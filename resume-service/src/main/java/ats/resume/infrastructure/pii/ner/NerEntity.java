package ats.resume.infrastructure.pii.ner;

/** Сущность NER в координатах исходного текста. */
public record NerEntity(String type, int start, int end, double confidence) {

    public static final String PERSON = "PER";
    public static final String LOCATION = "LOC";
    public static final String ORGANIZATION = "ORG";
}

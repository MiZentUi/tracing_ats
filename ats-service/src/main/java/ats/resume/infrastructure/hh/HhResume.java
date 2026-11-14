package ats.resume.infrastructure.hh;

/** Резюме, полученное из hh.ru и приведённое к обычному тексту. */
public record HhResume(String externalId, String title, String text) {}

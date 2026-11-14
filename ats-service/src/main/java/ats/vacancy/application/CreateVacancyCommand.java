package ats.vacancy.application;

import org.springframework.web.multipart.MultipartFile;

public record CreateVacancyCommand(
        String specialization,
        String level,
        String title,
        Integer experienceYears,
        String text,
        MultipartFile file) {}

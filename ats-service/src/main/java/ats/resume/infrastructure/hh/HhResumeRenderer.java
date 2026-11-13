package ats.resume.infrastructure.hh;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Превращает JSON резюме hh.ru в обычный текст для существующего пайплайна маскировки. Имя,
 * контакты, пол, возраст, дата рождения, фото, гражданство и семейное положение намеренно не
 * переносятся: для скоринга они не нужны, а данные минимизируем до профессиональных фактов.
 */
@Component
public class HhResumeRenderer {

    private static final int MAX_CHARS = 200_000;

    public HhResume render(String resumeId, JsonNode resume) {
        StringBuilder text = new StringBuilder();
        appendSection(text, "Желаемая должность", textValue(resume, "title"));
        appendSection(text, "О себе", textValue(resume, "skills"));
        appendSkills(text, resume);
        appendExperience(text, resume);
        appendEducation(text, resume);
        appendLanguages(text, resume);
        String result = text.toString().strip();
        if (result.length() > MAX_CHARS) {
            result = result.substring(0, MAX_CHARS);
        }
        return new HhResume(resumeId, textValue(resume, "title"), result);
    }

    private void appendSkills(StringBuilder text, JsonNode resume) {
        List<String> skills = new ArrayList<>();
        JsonNode skillSet = resume.path("skill_set");
        if (skillSet.isArray()) {
            skillSet.forEach(node -> addIfPresent(skills, node.asText(null)));
        }
        if (!skills.isEmpty()) {
            text.append("Навыки\n");
            skills.forEach(skill -> text.append("- ").append(skill).append('\n'));
        }
    }

    private void appendExperience(StringBuilder text, JsonNode resume) {
        JsonNode experience = resume.path("experience");
        if (!experience.isArray() || experience.isEmpty()) {
            return;
        }
        text.append("Опыт работы\n");
        for (JsonNode item : experience) {
            String company = textValue(item, "company");
            String position = textValue(item, "position");
            String start = textValue(item, "start");
            String end = textValue(item, "end");
            text.append("- ");
            if (company != null) {
                text.append(company);
            }
            if (position != null) {
                text.append(company == null ? "" : " — ").append(position);
            }
            if (start != null || end != null) {
                text.append(" (")
                        .append(start == null ? "?" : start)
                        .append(" — ")
                        .append(end == null ? "н.в." : end)
                        .append(')');
            }
            text.append('\n');
            String description = textValue(item, "description");
            if (description != null) {
                text.append(description).append('\n');
            }
        }
    }

    private void appendEducation(StringBuilder text, JsonNode resume) {
        JsonNode education = resume.path("education");
        if (education.isMissingNode() || education.isNull()) {
            return;
        }
        JsonNode primary = education.path("primary");
        List<String> lines = new ArrayList<>();
        if (primary.isArray()) {
            for (JsonNode item : primary) {
                List<String> parts = new ArrayList<>();
                addIfPresent(parts, textValue(item, "name"));
                addIfPresent(parts, textValue(item, "organization"));
                addIfPresent(parts, textValue(item, "result"));
                String year = textValue(item, "year");
                if (year != null) {
                    parts.add(year);
                }
                if (!parts.isEmpty()) {
                    lines.add(String.join(", ", parts));
                }
            }
        }
        String level = textValue(education.path("level"), "name");
        if (level != null) {
            lines.add(0, level);
        }
        if (!lines.isEmpty()) {
            text.append("Образование\n");
            lines.forEach(line -> text.append("- ").append(line).append('\n'));
        }
    }

    private void appendLanguages(StringBuilder text, JsonNode resume) {
        JsonNode languages = resume.path("language");
        if (!languages.isArray() || languages.isEmpty()) {
            return;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode language : languages) {
            String name = textValue(language, "name");
            String level = textValue(language.path("level"), "name");
            if (name != null) {
                values.add(level == null ? name : name + " — " + level);
            }
        }
        if (!values.isEmpty()) {
            text.append("Языки\n");
            values.forEach(value -> text.append("- ").append(value).append('\n'));
        }
    }

    private void appendSection(StringBuilder text, String title, String value) {
        if (value != null) {
            text.append(title).append('\n').append(value).append('\n');
        }
    }

    private static void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private static String textValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }
}

package ats.resume.infrastructure.hh;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class HhResumeRendererTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HhResumeRenderer renderer = new HhResumeRenderer();

    @Test
    void rendersProfessionalFactsAndContactsButSkipsProtectedAttributes() throws Exception {
        JsonNode resume =
                mapper.readTree(
                        """
                        {
                          "id": "resume-1",
                          "title": "Java-разработчик",
                          "first_name": "Иван",
                          "last_name": "Петров",
                          "middle_name": null,
                          "age": 29,
                          "gender": {"id": "male", "name": "Мужской"},
                          "birth_date": "1993-06-28",
                          "photo": {"small": "https://example.test/photo.png"},
                          "citizenship": [{"name": "Россия"}],
                          "contact": [
                            {"kind": "email", "contact_value": "ivan.petrov@example.com"},
                            {"kind": "phone", "contact_value": "+7 999 123-45-67"}
                          ],
                          "skills": "Пять лет разработки",
                          "skill_set": ["Java", "Spring Boot", "PostgreSQL"],
                          "experience": [
                            {
                              "company": "Компания",
                              "position": "Senior Java Developer",
                              "start": "2019-01-01",
                              "end": null,
                              "description": "Проектировал сервисы"
                            }
                          ],
                          "education": {
                            "level": {"name": "Высшее"},
                            "primary": [
                              {"name": "Университет", "organization": "Факультет", "result": "Прикладная математика", "year": 2018}
                            ]
                          },
                          "language": [{"name": "Английский", "level": {"name": "B2"}}]
                        }
                        """);

        HhResume result = renderer.render("resume-1", resume);

        assertThat(result.title()).isEqualTo("Java-разработчик");
        assertThat(result.text())
                .contains("Java")
                .contains("Spring Boot")
                .contains("Компания")
                .contains("Senior Java Developer")
                .contains("Университет")
                .contains("Английский");
        assertThat(result.text())
                .doesNotContain("Иван")
                .doesNotContain("Петров")
                .doesNotContain("ivan.petrov@example.com")
                .doesNotContain("+7 999 123-45-67")
                .doesNotContain("29")
                .doesNotContain("Мужской")
                .doesNotContain("1993-06-28")
                .doesNotContain("https://example.test/photo.png")
                .doesNotContain("Россия");
    }

    @Test
    void rendersEmptyResumeWithoutFailing() {
        HhResume result = renderer.render("empty", mapper.createObjectNode());

        assertThat(result.externalId()).isEqualTo("empty");
        assertThat(result.text()).isEmpty();
        assertThat(result.title()).isNull();
    }
}

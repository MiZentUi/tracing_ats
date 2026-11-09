package ats.vacancy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ats.vacancy.TestcontainersConfiguration;
import ats.vacancy.llm.LlmClient;
import ats.vacancy.llm.LlmResponse;
import ats.vacancy.llm.LlmUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class VacancyApiIntegrationTest {

    private static final String TEXT =
            """
            Backend-разработчик

            Обязательно:
            - Kafka
            - Опыт работы с JS от 3 лет
            """;

    private static final String LLM_JSON =
            """
            {"schema_version": 2, "title": "Backend-разработчик", "requirements": [
              {"skill": "JS", "kind": "NICE", "years_required": 3,
               "quote": "Опыт работы с JS от 3 лет", "clarity_warning": null},
              {"skill": "Kafka", "kind": "MUST", "years_required": null,
               "quote": "Kafka", "clarity_warning": null}
            ]}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private LlmClient llmClient;

    private static Map<String, Object> requirement(
            String id, String skill, String kind, Integer years, String quote, String warning) {
        Map<String, Object> requirement = new java.util.HashMap<>();
        requirement.put("id", id);
        requirement.put("skill", skill);
        requirement.put("kind", kind);
        requirement.put("years_required", years);
        requirement.put("quote", quote);
        requirement.put("clarity_warning", warning);
        return requirement;
    }

    @BeforeEach
    void stubLlm() {
        when(llmClient.complete(any()))
                .thenReturn(new LlmResponse(LLM_JSON, "mock/mock-model", 10, 20, "stop"));
    }

    private JsonNode createVacancy() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                multipart("/api/v1/vacancies")
                                        .param("specialization", "backend")
                                        .param("level", "middle")
                                        .param("text", TEXT))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void createsDraftAndSavesTemplateWithStats() throws Exception {
        JsonNode created = createVacancy();
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("template").path("version").asInt()).isEqualTo(1);
        assertThat(created.path("template").path("is_confirmed").asBoolean()).isFalse();
        assertThat(created.path("template").path("requirements")).hasSize(2);

        JsonNode javascript = created.path("template").path("requirements").get(0);
        assertThat(javascript.path("skill").asText()).isEqualTo("JavaScript");
        assertThat(javascript.path("normalization_status").asText()).isEqualTo("MATCHED");
        assertThat(javascript.path("kind").asText()).isEqualTo("NICE");
        assertThat(javascript.path("kind_source").asText()).isEqualTo("LLM");
        assertThat(javascript.path("llm_kind").asText()).isEqualTo("NICE");
        assertThat(javascript.path("skill_id").isNull()).isFalse();

        String vacancyId = created.path("vacancy_id").asText();
        String requirementId = javascript.path("id").asText();
        Map<String, Object> updateBody =
                Map.of(
                        "base_version",
                        1,
                        "specialization",
                        "backend",
                        "level",
                        "middle",
                        "requirements",
                        List.of(
                                requirement(
                                        requirementId,
                                        "JavaScript",
                                        "MUST",
                                        3,
                                        "Опыт работы с JS от 3 лет",
                                        null),
                                requirement(
                                        null,
                                        "Tauri",
                                        "OPTIONAL",
                                        null,
                                        "Tauri знать не обязательно, научим",
                                        null)));

        MvcResult saved =
                mockMvc.perform(
                                put("/api/v1/vacancies/{id}/template", vacancyId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(updateBody)))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode savedJson = objectMapper.readTree(saved.getResponse().getContentAsString());
        assertThat(savedJson.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(savedJson.path("template").path("version").asInt()).isEqualTo(2);
        assertThat(savedJson.path("template").path("is_confirmed").asBoolean()).isTrue();
        JsonNode changed = savedJson.path("template").path("requirements").get(0);
        assertThat(changed.path("kind").asText()).isEqualTo("MUST");
        assertThat(changed.path("kind_source").asText()).isEqualTo("HR");
        assertThat(changed.path("llm_kind").asText()).isEqualTo("NICE");

        Integer javascriptMust =
                jdbcTemplate.queryForObject(
                        "SELECT must_count FROM skill_kind_stat sks "
                                + "JOIN skill s ON s.id = sks.skill_id "
                                + "WHERE s.normalized_name = 'javascript'",
                        Integer.class);
        Integer tauriOptional =
                jdbcTemplate.queryForObject(
                        "SELECT optional_count FROM skill_kind_stat sks "
                                + "JOIN skill s ON s.id = sks.skill_id "
                                + "WHERE s.normalized_name = 'tauri'",
                        Integer.class);
        assertThat(javascriptMust).isEqualTo(1);
        assertThat(tauriOptional).isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM skill_alias WHERE alias_normalized = 'tauri'",
                                Integer.class))
                .isEqualTo(1);

        // Повторное сохранение без изменений не двигает счётчики.
        updateBody = new java.util.HashMap<>(updateBody);
        updateBody.put("base_version", 2);
        mockMvc.perform(
                        put("/api/v1/vacancies/{id}/template", vacancyId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT must_count FROM skill_kind_stat sks "
                                        + "JOIN skill s ON s.id = sks.skill_id "
                                        + "WHERE s.normalized_name = 'javascript'",
                                Integer.class))
                .isEqualTo(1);

        // Устаревшая версия: 409 и никаких новых версий.
        mockMvc.perform(
                        put("/api/v1/vacancies/{id}/template", vacancyId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isConflict())
                .andExpect(
                        result ->
                                assertThat(
                                                objectMapper
                                                        .readTree(
                                                                result.getResponse()
                                                                        .getContentAsString())
                                                        .path("status")
                                                        .asText())
                                        .isEqualTo("VERSION_CONFLICT"));

        Integer versions =
                jdbcTemplate.queryForObject(
                        "SELECT max(version_no) FROM vacancy_template_version", Integer.class);
        assertThat(versions).isEqualTo(3);
    }

    @Test
    void rejectsDuplicateSkills() throws Exception {
        JsonNode created = createVacancy();
        String vacancyId = created.path("vacancy_id").asText();
        String requirementId =
                created.path("template").path("requirements").get(0).path("id").asText();

        Map<String, Object> updateBody =
                Map.of(
                        "base_version",
                        1,
                        "requirements",
                        List.of(
                                requirement(
                                        requirementId,
                                        "JS",
                                        "MUST",
                                        null,
                                        "Опыт работы с JS от 3 лет",
                                        null),
                                requirement(null, "JavaScript", "NICE", null, null, null)));
        mockMvc.perform(
                        put("/api/v1/vacancies/{id}/template", vacancyId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        result ->
                                assertThat(
                                                objectMapper
                                                        .readTree(
                                                                result.getResponse()
                                                                        .getContentAsString())
                                                        .path("status")
                                                        .asText())
                                        .isEqualTo("DUPLICATE_SKILL"));
    }

    @Test
    void acceptsTextFileAndListsVacancies() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "vacancy.txt", "text/plain", TEXT.getBytes(StandardCharsets.UTF_8));
        MvcResult result =
                mockMvc.perform(
                                multipart("/api/v1/vacancies")
                                        .file(file)
                                        .param("specialization", "backend")
                                        .param("level", "middle"))
                        .andExpect(status().isCreated())
                        .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(created.path("title").asText()).isEqualTo("Backend-разработчик");

        mockMvc.perform(get("/api/v1/vacancies").param("limit", "10").param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(
                        response ->
                                assertThat(
                                                objectMapper
                                                        .readTree(
                                                                response.getResponse()
                                                                        .getContentAsString())
                                                        .path("items")
                                                        .size())
                                        .isEqualTo(1));
    }

    @Test
    void ignoresWeightAndCriticalInUpdateRequest() throws Exception {
        JsonNode created = createVacancy();
        String vacancyId = created.path("vacancy_id").asText();
        String requirementId =
                created.path("template").path("requirements").get(0).path("id").asText();

        Map<String, Object> requirement =
                requirement(
                        requirementId, "JavaScript", "MUST", 3, "Опыт работы с JS от 3 лет", null);
        requirement.put("weight", 3);
        requirement.put("critical", true);
        Map<String, Object> body = Map.of("base_version", 1, "requirements", List.of(requirement));

        MvcResult result =
                mockMvc.perform(
                                put("/api/v1/vacancies/{id}/template", vacancyId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(body)))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode saved = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode savedRequirement = saved.path("template").path("requirements").get(0);
        assertThat(savedRequirement.has("weight")).isFalse();
        assertThat(savedRequirement.has("critical")).isFalse();
    }

    @Test
    void returnsBadGatewayForInvalidLlmResponse() throws Exception {
        when(llmClient.complete(any()))
                .thenReturn(new LlmResponse("не json", "mock/mock-model", 1, 1, "stop"));

        mockMvc.perform(
                        multipart("/api/v1/vacancies")
                                .param("specialization", "backend")
                                .param("level", "middle")
                                .param("text", TEXT))
                .andExpect(status().isBadGateway())
                .andExpect(
                        result ->
                                assertThat(
                                                objectMapper
                                                        .readTree(
                                                                result.getResponse()
                                                                        .getContentAsString())
                                                        .path("status")
                                                        .asText())
                                        .isEqualTo("LLM_INVALID_RESPONSE"));
    }

    @Test
    void createsVacancyWithEmptyTemplateWhenLlmIsUnavailable() throws Exception {
        when(llmClient.complete(any())).thenThrow(new LlmUnavailableException("LLM is down", null));

        JsonNode created = createVacancy();
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("template").path("requirements")).isEmpty();
        assertThat(created.path("template").path("is_confirmed").asBoolean()).isFalse();
    }

    @Test
    void returnsReferenceDictionaries() throws Exception {
        mockMvc.perform(get("/api/v1/specializations"))
                .andExpect(status().isOk())
                .andExpect(
                        response ->
                                assertThat(
                                                objectMapper
                                                        .readTree(
                                                                response.getResponse()
                                                                        .getContentAsString())
                                                        .size())
                                        .isGreaterThanOrEqualTo(7));
        mockMvc.perform(get("/api/v1/levels"))
                .andExpect(status().isOk())
                .andExpect(
                        response ->
                                assertThat(
                                                objectMapper
                                                        .readTree(
                                                                response.getResponse()
                                                                        .getContentAsString())
                                                        .size())
                                        .isEqualTo(4));
    }
}

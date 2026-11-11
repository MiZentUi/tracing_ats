package ats.vacancy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ats.vacancy.TestcontainersConfiguration;
import ats.vacancy.llm.LlmClient;
import ats.vacancy.llm.LlmResponse;
import ats.vacancy.llm.LlmUnavailableException;
import ats.vacancy.template.VacancyTemplateData;
import ats.vacancy.template.VacancyTemplateQuery;
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
    @Autowired private VacancyTemplateQuery vacancyTemplateQuery;
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
        assertThat(created.path("source_text").asText()).isEqualTo(TEXT.strip());
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
                        response -> {
                            JsonNode items =
                                    objectMapper
                                            .readTree(response.getResponse().getContentAsString())
                                            .path("items");
                            // В схеме есть демо-вакансия из миграций, поэтому проверяем
                            // наличие созданной, а не общее количество.
                            assertThat(items.findValuesAsText("id"))
                                    .contains(created.path("vacancy_id").asText());
                        });
    }

    @Test
    void confirmsDraftWithoutChanges() throws Exception {
        JsonNode created = createVacancy();
        String vacancyId = created.path("vacancy_id").asText();
        List<Map<String, Object>> requirements = new java.util.ArrayList<>();
        for (JsonNode item : created.path("template").path("requirements")) {
            JsonNode years = item.path("years_required");
            requirements.add(
                    requirement(
                            item.path("id").asText(),
                            item.path("skill").asText(),
                            item.path("kind").asText(),
                            years.isNull() ? null : years.asInt(),
                            item.path("quote").asText(),
                            item.path("clarity_warning").isNull()
                                    ? null
                                    : item.path("clarity_warning").asText()));
        }

        MvcResult result =
                mockMvc.perform(
                                put("/api/v1/vacancies/{id}/template", vacancyId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "base_version",
                                                                1,
                                                                "requirements",
                                                                requirements))))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode saved = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(saved.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(saved.path("template").path("version").asInt()).isEqualTo(2);
        assertThat(saved.path("template").path("is_confirmed").asBoolean()).isTrue();
    }

    @Test
    void requiresLevelForItSpecialization() throws Exception {
        mockMvc.perform(
                        multipart("/api/v1/vacancies")
                                .param("specialization", "backend")
                                .param("text", TEXT))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("LEVEL_REQUIRED"));
    }

    @Test
    void createsNonItVacancyWithoutLevel() throws Exception {
        mockMvc.perform(
                        post("/api/v1/specializations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Аналитика\",\"it\":false}"))
                .andExpect(status().isCreated());

        MvcResult result =
                mockMvc.perform(
                                multipart("/api/v1/vacancies")
                                        .param("specialization", "analitika")
                                        .param("text", TEXT))
                        .andExpect(status().isCreated())
                        .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(created.path("level").asText()).isEqualTo("none");
    }

    @Test
    void ignoresLevelForNonItSpecialization() throws Exception {
        mockMvc.perform(
                        post("/api/v1/specializations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Аналитика\",\"it\":false}"))
                .andExpect(status().isCreated());

        MvcResult result =
                mockMvc.perform(
                                multipart("/api/v1/vacancies")
                                        .param("specialization", "analitika")
                                        .param("level", "senior")
                                        .param("text", TEXT))
                        .andExpect(status().isCreated())
                        .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(created.path("level").asText()).isEqualTo("none");
    }

    @Test
    void createsAndDeletesVacancyWithStatsRollback() throws Exception {
        JsonNode created = createVacancy();
        String vacancyId = created.path("vacancy_id").asText();
        JsonNode javascript = created.path("template").path("requirements").get(0);
        Map<String, Object> requirement =
                requirement(
                        javascript.path("id").asText(),
                        "JavaScript",
                        "MUST",
                        3,
                        "Опыт работы с JS от 3 лет",
                        null);
        mockMvc.perform(
                        put("/api/v1/vacancies/{id}/template", vacancyId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "base_version",
                                                        1,
                                                        "requirements",
                                                        List.of(requirement)))))
                .andExpect(status().isOk());

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT must_count FROM skill_kind_stat sks "
                                        + "JOIN skill s ON s.id = sks.skill_id "
                                        + "WHERE s.normalized_name = 'javascript'",
                                Integer.class))
                .isEqualTo(1);

        mockMvc.perform(delete("/api/v1/vacancies/{id}", vacancyId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/vacancies/{id}", vacancyId)).andExpect(status().isNotFound());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT must_count FROM skill_kind_stat sks "
                                        + "JOIN skill s ON s.id = sks.skill_id "
                                        + "WHERE s.normalized_name = 'javascript'",
                                Integer.class))
                .isZero();
    }

    @Test
    void createsSpecializationAndRejectsDuplicate() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/specializations")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"Аналитика\",\"it\":false}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(created.path("code").asText()).isEqualTo("analitika");
        assertThat(created.path("name").asText()).isEqualTo("Аналитика");
        assertThat(created.path("it").asBoolean()).isFalse();

        mockMvc.perform(
                        post("/api/v1/specializations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Аналитика\",\"it\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsSpecializationWithoutItFlag() throws Exception {
        // Поле it помечено required в контракте, поэтому срабатывает @NotNull-валидация DTO.
        mockMvc.perform(
                        post("/api/v1/specializations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Аналитика\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("VALIDATION_ERROR"));
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
    void readsDemoTemplateForInternalGrpc() {
        // Регресс: gRPC-фасад читает шаблон SQL-запросом. После 009 у требования нет
        // колонки weight, и запрос должен выводить вес из kind, а не падать.
        VacancyTemplateData template =
                vacancyTemplateQuery
                        .findCurrent(
                                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"))
                        .orElseThrow();

        assertThat(template.requirements()).hasSize(6);
        assertThat(template.requirements())
                .extracting(VacancyTemplateData.RequirementData::weight)
                .containsExactly(3, 3, 3, 2, 2, 1);
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

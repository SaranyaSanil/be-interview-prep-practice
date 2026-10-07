package com.interviewprep.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end through Controller → Service → Repository → H2, with a fixed clock so dates are deterministic.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskApiIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2030, 1, 15);
    private static final LocalDate FUTURE = TODAY.plusDays(30);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void cleanDatabase() {
        taskRepository.deleteAll();
    }

    @Test
    void createGetUpdateAndDeleteTask() throws Exception {
        long id = createTask("Prepare demo", "TODO");

        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Prepare demo"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.dueDate").value(FUTURE.toString()))
                .andExpect(jsonPath("$.createdDate").value(TODAY.toString()));

        mockMvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Prepare final demo", "description": "Slides + live run",
                                 "status": "IN_PROGRESS", "dueDate": "%s"}
                                """.formatted(FUTURE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Prepare final demo"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.createdDate").value(TODAY.toString()));

        mockMvc.perform(delete("/api/tasks/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isNotFound());
        assertThat(taskRepository.count()).isZero();
    }

    @Test
    void listFiltersByStatus() throws Exception {
        createTask("Task A", "TODO");
        createTask("Task B", "DONE");
        createTask("Task C", "DONE");

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get("/api/tasks").param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Task B"))
                .andExpect(jsonPath("$[1].title").value("Task C"));
    }

    @Test
    void clientCannotSetCreatedDate() throws Exception {
        String body = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Sneaky", "createdDate": "1999-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(body, "$.createdDate")).isEqualTo(TODAY.toString());
    }

    @Test
    void createRejectsDueDateBeforeTodayAccordingToApplicationClock() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Late", "dueDate": "%s"}
                                """.formatted(TODAY.minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"));
    }

    @Test
    void deleteUnknownTaskReturns404() throws Exception {
        mockMvc.perform(delete("/api/tasks/{id}", 12345))
                .andExpect(status().isNotFound());
    }

    private long createTask(String title, String status) throws Exception {
        String body = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "status": "%s", "dueDate": "%s"}
                                """.formatted(title, status, FUTURE)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}

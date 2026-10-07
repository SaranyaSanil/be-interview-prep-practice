package com.interviewprep.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.interviewprep.common.exception.FieldValidationException;
import com.interviewprep.common.exception.ResourceNotFoundException;
import com.interviewprep.task.dto.CreateTaskRequest;
import com.interviewprep.task.dto.TaskResponse;
import com.interviewprep.task.dto.UpdateTaskRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests: HTTP mapping, Bean Validation and error translation. The service is mocked.
 */
@WebMvcTest(TaskController.class)
class TaskControllerTest {

    private static final LocalDate FUTURE = LocalDate.now().plusYears(1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(taskService.create(any(CreateTaskRequest.class))).thenReturn(
                new TaskResponse(1L, "Write report", null, TaskStatus.TODO, FUTURE, LocalDate.now()));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Write report", "dueDate": "%s"}
                                """.formatted(FUTURE)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/tasks/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void createRejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
        verifyNoInteractions(taskService);
    }

    @Test
    void createRejectsTitleLongerThan100Characters() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s"}
                                """.formatted("x".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value("size must be between 0 and 100"));
        verifyNoInteractions(taskService);
    }

    @Test
    void createRejectsPastDueDate() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Late task", "dueDate": "2000-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a date in the present or in the future"));
        verifyNoInteractions(taskService);
    }

    @Test
    void updateRejectsUnknownStatus() throws Exception {
        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Task", "status": "BLOCKED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("must be one of [TODO, IN_PROGRESS, DONE]"));
    }

    @Test
    void createRejectsInvalidDateFormat() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Task", "dueDate": "07/10/2026"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"));
    }

    @Test
    void listRejectsUnknownStatusFilter() throws Exception {
        mockMvc.perform(get("/api/tasks").param("status", "BLOCKED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    @Test
    void getUnknownTaskReturns404() throws Exception {
        when(taskService.findById(99L)).thenThrow(new ResourceNotFoundException("Task 99 not found"));

        mockMvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Task 99 not found"));
    }

    @Test
    void updateRequiresStatus() throws Exception {
        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Task"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be null"));
    }

    @Test
    void updateReturns400WhenServiceRejectsDueDate() throws Exception {
        when(taskService.update(eq(1L), any(UpdateTaskRequest.class)))
                .thenThrow(new FieldValidationException("dueDate", "must be a date in the present or in the future"));

        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Task", "status": "TODO", "dueDate": "2000-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"));
    }
}

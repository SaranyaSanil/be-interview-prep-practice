package com.interviewprep.task.dto;

import java.time.LocalDate;

import com.interviewprep.task.Task;
import com.interviewprep.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Full-replacement (PUT) payload. The "due date not in the past" rule is enforced by the service, because an
 * existing task whose due date has already passed must still be updatable (e.g. marked DONE) without changing it.
 */
public record UpdateTaskRequest(
        @NotBlank @Size(max = Task.TITLE_MAX_LENGTH) String title,
        @Size(max = Task.DESCRIPTION_MAX_LENGTH) String description,
        @NotNull TaskStatus status,
        LocalDate dueDate) {
}

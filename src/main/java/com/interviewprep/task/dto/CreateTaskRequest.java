package com.interviewprep.task.dto;

import java.time.LocalDate;

import com.interviewprep.task.Task;
import com.interviewprep.task.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create payload. {@code status} is optional and defaults to {@link TaskStatus#TODO}.
 * There is deliberately no {@code id} or {@code createdDate}: both are server-generated.
 */
public record CreateTaskRequest(
        @NotBlank @Size(max = Task.TITLE_MAX_LENGTH) String title,
        @Size(max = Task.DESCRIPTION_MAX_LENGTH) String description,
        TaskStatus status,
        @FutureOrPresent LocalDate dueDate) {
}

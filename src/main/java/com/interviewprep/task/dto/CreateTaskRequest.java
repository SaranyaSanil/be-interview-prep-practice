package com.interviewprep.task.dto;

import java.time.LocalDate;

import com.interviewprep.task.Task;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create payload. There is deliberately no {@code id}, {@code status} or {@code createdDate}: they are set by the
 * server (new tasks always start as TODO; status is changed through an update).
 */
public record CreateTaskRequest(
        @NotBlank @Size(max = Task.TITLE_MAX_LENGTH) String title,
        @Size(max = Task.DESCRIPTION_MAX_LENGTH) String description,
        @FutureOrPresent LocalDate dueDate) {
}

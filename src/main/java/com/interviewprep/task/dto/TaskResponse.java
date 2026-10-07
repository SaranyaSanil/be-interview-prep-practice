package com.interviewprep.task.dto;

import java.time.LocalDate;

import com.interviewprep.task.Task;
import com.interviewprep.task.TaskStatus;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        LocalDate dueDate,
        LocalDate createdDate) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getDueDate(),
                task.getCreatedDate());
    }
}

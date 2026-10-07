package com.interviewprep.task;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.interviewprep.common.exception.FieldValidationException;
import com.interviewprep.common.exception.ResourceNotFoundException;
import com.interviewprep.task.dto.CreateTaskRequest;
import com.interviewprep.task.dto.TaskResponse;
import com.interviewprep.task.dto.UpdateTaskRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private static final Sort DEFAULT_SORT = Sort.by("id");

    private final TaskRepository taskRepository;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        TaskStatus status = Objects.requireNonNullElse(request.status(), TaskStatus.TODO);
        Task task = new Task(request.title(), request.description(), status, request.dueDate(), today());
        return TaskResponse.from(taskRepository.save(task));
    }

    public List<TaskResponse> findAll(TaskStatus status) {
        List<Task> tasks = status == null
                ? taskRepository.findAll(DEFAULT_SORT)
                : taskRepository.findByStatus(status, DEFAULT_SORT);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    public TaskResponse findById(Long id) {
        return TaskResponse.from(getTask(id));
    }

    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request) {
        Task task = getTask(id);
        validateDueDateChange(task.getDueDate(), request.dueDate());
        task.update(request.title(), request.description(), request.status(), request.dueDate());
        return TaskResponse.from(task);
    }

    @Transactional
    public void delete(Long id) {
        taskRepository.delete(getTask(id));
    }

    private Task getTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task " + id + " not found"));
    }

    /** A due date may not be moved into the past, but an already-past due date may be kept unchanged. */
    private void validateDueDateChange(LocalDate currentDueDate, LocalDate newDueDate) {
        boolean changed = !Objects.equals(currentDueDate, newDueDate);
        if (changed && newDueDate != null && newDueDate.isBefore(today())) {
            throw new FieldValidationException("dueDate", "must be a date in the present or in the future");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}

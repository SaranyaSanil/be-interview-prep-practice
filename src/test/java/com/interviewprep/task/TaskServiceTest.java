package com.interviewprep.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.interviewprep.common.exception.FieldValidationException;
import com.interviewprep.common.exception.ResourceNotFoundException;
import com.interviewprep.task.dto.CreateTaskRequest;
import com.interviewprep.task.dto.TaskResponse;
import com.interviewprep.task.dto.UpdateTaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    @Mock
    private TaskRepository taskRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        taskService = new TaskService(taskRepository, fixedClock);
    }

    @Test
    void createSetsCreatedDateToTodayAndStatusToTodo() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(
                new CreateTaskRequest("Write tests", null, TODAY.plusDays(3)));

        assertThat(response.createdDate()).isEqualTo(TODAY);
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.dueDate()).isEqualTo(TODAY.plusDays(3));
    }

    @Test
    void findAllFiltersByStatusWhenGiven() {
        Task done = new Task("Done task", null, TaskStatus.DONE, null, TODAY);
        when(taskRepository.findByStatus(TaskStatus.DONE, Sort.by("id"))).thenReturn(List.of(done));

        List<TaskResponse> result = taskService.findAll(TaskStatus.DONE);

        assertThat(result).extracting(TaskResponse::title).containsExactly("Done task");
        verify(taskRepository, never()).findAll(any(Sort.class));
    }

    @Test
    void findAllReturnsAllTasksWhenNoStatusGiven() {
        when(taskRepository.findAll(Sort.by("id"))).thenReturn(List.of(
                new Task("A", null, TaskStatus.TODO, null, TODAY),
                new Task("B", null, TaskStatus.DONE, null, TODAY)));

        assertThat(taskService.findAll(null)).extracting(TaskResponse::title).containsExactly("A", "B");
        verify(taskRepository, never()).findByStatus(any(), any());
    }

    @Test
    void findByIdThrowsWhenTaskDoesNotExist() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Task 99 not found");
    }

    @Test
    void updateRejectsChangingDueDateToThePast() {
        Task task = new Task("Task", null, TaskStatus.TODO, TODAY.plusDays(1), TODAY);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        UpdateTaskRequest request = new UpdateTaskRequest("Task", null, TaskStatus.TODO, TODAY.minusDays(1));

        assertThatThrownBy(() -> taskService.update(1L, request))
                .isInstanceOf(FieldValidationException.class)
                .extracting("field").isEqualTo("dueDate");
        assertThat(task.getDueDate()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void updateAllowsMovingDueDateToTodayOrLater() {
        Task task = new Task("Task", null, TaskStatus.TODO, TODAY.plusDays(1), TODAY);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskResponse response = taskService.update(
                1L, new UpdateTaskRequest("Renamed", "Details", TaskStatus.IN_PROGRESS, TODAY));

        assertThat(response.title()).isEqualTo("Renamed");
        assertThat(response.description()).isEqualTo("Details");
        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.dueDate()).isEqualTo(TODAY);
    }

    @Test
    void updateAllowsKeepingAnAlreadyPastDueDate() {
        LocalDate overdue = TODAY.minusDays(5);
        Task task = new Task("Overdue task", null, TaskStatus.IN_PROGRESS, overdue, TODAY.minusDays(10));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskResponse response = taskService.update(
                1L, new UpdateTaskRequest("Overdue task", null, TaskStatus.DONE, overdue));

        assertThat(response.status()).isEqualTo(TaskStatus.DONE);
        assertThat(response.dueDate()).isEqualTo(overdue);
        assertThat(response.createdDate()).isEqualTo(TODAY.minusDays(10));
    }

    @Test
    void deleteThrowsWhenTaskDoesNotExist() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.delete(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).delete(any(Task.class));
    }
}

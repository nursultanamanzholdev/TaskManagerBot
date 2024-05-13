package com.nursultan.TaskMasterMindBot.TaskBotModel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    Optional<Task> findByName(String name);
    List<Task> findByUser(User user);
    Optional<Task> findByIdAndUser_ChatId(long taskId, long chatId);
}

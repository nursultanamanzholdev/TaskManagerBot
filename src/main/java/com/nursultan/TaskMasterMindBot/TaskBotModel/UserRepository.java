package com.nursultan.TaskMasterMindBot.TaskBotModel;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    User findById(long chatId);
}

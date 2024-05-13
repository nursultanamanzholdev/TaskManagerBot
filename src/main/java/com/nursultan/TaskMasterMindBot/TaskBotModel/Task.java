package com.nursultan.TaskMasterMindBot.TaskBotModel;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "TASKS")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column()
    private Priority priority;

    @Column
    private String description;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "user_chat_id", nullable = false)
    private User user;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public enum Priority {
        High, Medium, Low;
    }
}


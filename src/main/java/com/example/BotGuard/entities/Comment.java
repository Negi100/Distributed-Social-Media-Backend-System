package com.example.BotGuard.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
@Entity
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long authId;
    private String authorType;
    private String content;
    private int depthLevel;
    private LocalDateTime timing =LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "post_id")
    @JsonBackReference
    private Post post;
}

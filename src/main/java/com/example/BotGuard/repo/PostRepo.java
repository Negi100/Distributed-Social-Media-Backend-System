package com.example.BotGuard.repo;

import com.example.BotGuard.entities.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepo extends JpaRepository<Post, Long> {
}

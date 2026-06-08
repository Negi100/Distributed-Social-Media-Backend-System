package com.example.BotGuard.repo;

import com.example.BotGuard.entities.Bot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotRepo extends JpaRepository<Bot, Long> {

}

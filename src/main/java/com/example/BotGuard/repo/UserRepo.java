package com.example.BotGuard.repo;

import com.example.BotGuard.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepo extends JpaRepository<User, Long> {
}

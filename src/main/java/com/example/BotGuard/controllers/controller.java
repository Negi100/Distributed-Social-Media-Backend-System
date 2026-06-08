package com.example.BotGuard.controllers;

import com.example.BotGuard.Services.NotificationService;
import com.example.BotGuard.Services.PostService;
import com.example.BotGuard.Services.RedisGuardrailService;
import com.example.BotGuard.entities.Comment;
import com.example.BotGuard.entities.Post;
import com.example.BotGuard.repo.CommentRepo;
import com.example.BotGuard.repo.PostRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class controller {

    private final PostRepo postRepo;
    private final CommentRepo commentRepo;
    private final PostService postService;
    private final RedisGuardrailService redisGuardrailService;
    private final NotificationService notificationService;

@PostMapping
    public ResponseEntity<Post> createPost(@RequestBody Post post){
        return ResponseEntity.ok(postRepo.save(post));
    }

    @GetMapping
    public ResponseEntity<List<Post>> getAllPosts() {
        return ResponseEntity.ok(postRepo.findAll());
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long postId, @RequestBody Comment comment) {
        try {
            Comment savedComment = postService.addComment(postId, comment);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedComment);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Guardrail")) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<String> likePost(@PathVariable Long postId) {
        redisGuardrailService.updateVirality(postId, "HUMAN_LIKE");
        return ResponseEntity.ok("Post Liked! Virality Updated.");
    }

}

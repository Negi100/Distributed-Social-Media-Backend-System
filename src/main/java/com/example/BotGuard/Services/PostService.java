package com.example.BotGuard.Services;

import com.example.BotGuard.entities.Comment;
import com.example.BotGuard.entities.Post;
import com.example.BotGuard.repo.CommentRepo;
import com.example.BotGuard.repo.PostRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepo postRepo;
    private final CommentRepo commentRepo;
    private final RedisGuardrailService redisGuardrailService;
    private final NotificationService notificationService;

//    public Post createPost(Post post) {
//        return postRepo.save(post);
//    }

    @Transactional
    public Comment addComment(Long postId, Comment comment) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if ("BOT".equalsIgnoreCase(comment.getAuthorType())) {
            // Redis atomic operations
            boolean isAllowed = redisGuardrailService.validateBotGuardrails(
                    comment.getAuthId(),
                    postId,
                    post.getAuthId(),
                    comment.getDepthLevel()
            );

            if (!isAllowed) {
                throw new RuntimeException("Guardrail: Bot limit or cooldown active");
            }

            notificationService.handleNotification(post.getAuthId(), "Bot " + comment.getAuthId());

            redisGuardrailService.updateVirality(postId, "BOT_REPLY");
        } else {
            redisGuardrailService.updateVirality(postId, "HUMAN_COMMENT");
        }
        comment.setPost(post);
        return commentRepo.save(comment);
    }

}

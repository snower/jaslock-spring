package io.github.snower.jaslock.spring.example.pubsub.controller.backend;

import io.github.snower.jaslock.spring.example.pubsub.dto.TopicDto;
import io.github.snower.jaslock.spring.example.pubsub.entity.PubsubTopic;
import io.github.snower.jaslock.spring.example.pubsub.service.PubsubTopicService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/backend/pubsub/message")
@Validated
public class PubsubTopicController {
    @Autowired
    private PubsubTopicService pubsubTopicService;

    @GetMapping("/getTopic")
    public PubsubTopic getTopic(@RequestParam @Valid @NotBlank String topicKey) {
        return pubsubTopicService.getTopic(topicKey);
    }

    @PostMapping("/createTopic")
    public PubsubTopic createTopic(@RequestBody @Valid @NotNull TopicDto topicDto) {
        return pubsubTopicService.createTopic(topicDto.getTopicKey());
    }

    @PostMapping("/getOrCreateTopic")
    public PubsubTopic getOrCreateTopic(@RequestBody @Valid @NotNull TopicDto topicDto) {
        return pubsubTopicService.createTopic(topicDto.getTopicKey());
    }
}

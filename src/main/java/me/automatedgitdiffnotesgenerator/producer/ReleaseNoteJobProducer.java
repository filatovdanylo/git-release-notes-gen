package me.automatedgitdiffnotesgenerator.producer;

import me.automatedgitdiffnotesgenerator.config.RabbitConfig;
import me.automatedgitdiffnotesgenerator.job.ReleaseNoteJob;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReleaseNoteJobProducer {
    private final RabbitTemplate rabbitTemplate;
    public ReleaseNoteJobProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void releaseNoteJob(ReleaseNoteJob releaseNoteJob) {
        rabbitTemplate.convertAndSend(RabbitConfig.QUEUE_NAME, releaseNoteJob);
    }
}

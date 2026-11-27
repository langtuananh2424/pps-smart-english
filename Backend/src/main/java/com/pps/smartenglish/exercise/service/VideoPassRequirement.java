package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.config.VideoQuizProperties;
import org.springframework.stereotype.Component;

/** D-159: số lượt đạt để bộ video hoàn thành; truy vấn tiến độ unit đọc qua SpEL nên cần bean có tên cố định. */
@Component("videoPassRequirement")
public class VideoPassRequirement {

    private final VideoQuizProperties props;

    VideoPassRequirement(VideoQuizProperties props) {
        this.props = props;
    }

    public int required() {
        return props.passesRequired();
    }
}

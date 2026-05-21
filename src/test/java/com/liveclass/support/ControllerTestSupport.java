package com.liveclass.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liveclass.course.service.CourseCreateService;
import com.liveclass.course.service.CourseStatusUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@ActiveProfiles("test")
public abstract class ControllerTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected CourseCreateService courseCreateService;

    @MockitoBean
    protected CourseStatusUpdateService courseStatusUpdateService;
}

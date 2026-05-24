package com.liveclass.support;

import com.liveclass.common.config.JpaConfig;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.member.repository.MemberRepository;
import com.liveclass.waitlist.repository.WaitlistRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class JpaTestSupport {

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected CourseRepository courseRepository;

    @Autowired
    protected CourseEnrollCountRepository courseEnrollCountRepository;

    @Autowired
    protected EnrollmentRepository enrollmentRepository;

    @Autowired
    protected MemberRepository memberRepository;

    @Autowired
    protected WaitlistRepository waitlistRepository;
}

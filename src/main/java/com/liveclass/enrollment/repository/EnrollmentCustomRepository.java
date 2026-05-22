package com.liveclass.enrollment.repository;

import com.liveclass.enrollment.dto.response.MyEnrollmentResponse;
import com.liveclass.enrollment.dto.response.StudentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface EnrollmentCustomRepository {

    List<StudentResponse> findStudentsByCourse(Long courseId);

    Page<MyEnrollmentResponse> findMyEnrollments(Long memberId, Pageable pageable);
}

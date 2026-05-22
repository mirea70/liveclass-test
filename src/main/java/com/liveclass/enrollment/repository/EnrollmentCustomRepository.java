package com.liveclass.enrollment.repository;

import com.liveclass.enrollment.dto.response.StudentResponse;

import java.util.List;

public interface EnrollmentCustomRepository {

    List<StudentResponse> findStudentsByCourse(Long courseId);
}

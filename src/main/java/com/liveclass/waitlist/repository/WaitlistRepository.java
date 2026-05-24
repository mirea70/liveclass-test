package com.liveclass.waitlist.repository;

import com.liveclass.waitlist.domain.entity.Waitlist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long>, WaitlistCustomRepository {

    boolean existsByCourseIdAndMemberId(Long courseId, Long memberId);
}

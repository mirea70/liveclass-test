package com.liveclass.common.initializer;

import com.liveclass.member.domain.entity.Member;
import com.liveclass.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberDataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;

    private static final List<String> TEST_MEMBER_NAMES = List.of(
            "홍길동",
            "이몽룡",
            "성춘향",
            "김철수",
            "이영희"
    );

    @Override
    @Transactional
    public void run(String... args) {
        if (memberRepository.count() > 0) {
            log.info("회원 데이터가 이미 존재합니다. 테스트 데이터 삽입을 건너뜁니다.");
            return;
        }

        TEST_MEMBER_NAMES.forEach(name -> {
            Member member = Member.createNew(name);
            memberRepository.save(member);
        });

        log.info("테스트 회원 {}명을 DB에 초기화했습니다. (테스트 편의용)", TEST_MEMBER_NAMES.size());
    }
}

package com.liveclass.common.initializer;

import com.liveclass.member.domain.entity.Member;
import com.liveclass.support.JpaTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemberDataInitializer 테스트")
class MemberDataInitializerTest extends JpaTestSupport {

    @Test
    @DisplayName("회원 데이터가 없으면 테스트 회원 5명을 생성한다")
    void seedsFiveMembers_whenNoMembersExist() throws Exception {
        // given
        assertThat(memberRepository.count()).isZero();

        // when
        MemberDataInitializer initializer = new MemberDataInitializer(memberRepository);
        initializer.run();

        // then
        assertThat(memberRepository.count()).isEqualTo(5L);

        List<String> names = memberRepository.findAll().stream()
                .map(Member::getName)
                .toList();

        assertThat(names).containsExactlyInAnyOrder(
                "홍길동", "이몽룡", "성춘향", "김철수", "이영희"
        );
    }

    @Test
    @DisplayName("이미 회원이 존재하면 추가로 생성하지 않는다")
    void doesNotSeed_whenMembersAlreadyExist() throws Exception {
        // given
        memberRepository.save(Member.createNew("기존회원"));
        assertThat(memberRepository.count()).isEqualTo(1L);

        // when
        MemberDataInitializer initializer = new MemberDataInitializer(memberRepository);
        initializer.run();

        // then
        assertThat(memberRepository.count()).isEqualTo(1L);
        assertThat(memberRepository.findAll().get(0).getName()).isEqualTo("기존회원");
    }
}

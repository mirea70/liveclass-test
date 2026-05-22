package com.liveclass.member.repository;

import com.liveclass.member.domain.entity.Member;
import com.liveclass.support.JpaTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemberRepository 슬라이스 테스트")
class MemberRepositoryTest extends JpaTestSupport {

    @Test
    @DisplayName("회원을 저장하면 ID와 감사 필드가 자동 설정된다")
    void generatesIdAndAuditFields_whenSaved() {
        // when
        Member saved = memberRepository.saveAndFlush(Member.createNew("홍길동"));

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("저장된 회원을 ID로 조회할 수 있다")
    void findsById_whenSaved() {
        // given
        Member saved = memberRepository.saveAndFlush(Member.createNew("이몽룡"));
        entityManager.clear();

        // when
        Member loaded = memberRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(loaded.getName()).isEqualTo("이몽룡");
    }
}

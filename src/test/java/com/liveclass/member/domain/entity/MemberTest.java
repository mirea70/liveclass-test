package com.liveclass.member.domain.entity;

import com.liveclass.common.error.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Member 도메인 테스트")
class MemberTest {

    @Test
    @DisplayName("회원을 생성하면 이름이 보관된다")
    void preservesName_whenCreated() {
        // when
        Member member = Member.createNew("홍길동");

        // then
        assertThat(member.getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("이름이 null이면 DomainException이 발생한다")
    void throws_whenNameIsNull() {
        // when & then
        assertThatThrownBy(() -> Member.createNew(null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("이름이 빈 문자열이면 DomainException이 발생한다")
    void throws_whenNameIsBlank() {
        // when & then
        assertThatThrownBy(() -> Member.createNew(""))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Member.createNew("   "))
                .isInstanceOf(DomainException.class);
    }
}

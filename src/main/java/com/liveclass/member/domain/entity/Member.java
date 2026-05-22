package com.liveclass.member.domain.entity;

import com.liveclass.common.domain.entity.BaseEntity;
import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.MemberErrorInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    private Member(String name) {
        this.name = name;
    }

    public static Member createNew(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException(MemberErrorInfo.MEMBER_NAME_EMPTY);
        }
        return new Member(name);
    }
}

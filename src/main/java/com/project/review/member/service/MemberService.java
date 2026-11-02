package com.project.review.member.service;

import com.project.review.auth.dtos.SignupRequest;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import com.project.review.member.dtos.MemberResponse;
import com.project.review.member.entities.Member;
import com.project.review.member.exception.DuplicateMemberException;
import com.project.review.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberResponse create(SignupRequest request) {

        existsEmail(request.email());
        existsNickname(request.nickname());

        Member member = Member.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .build();

        return MemberResponse.from(memberRepository.save(member));
    }

    @Transactional(readOnly = true)
    public Member findByEmail(String email){
        return memberRepository.findByEmail(email.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Member findById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void existsEmail(String email){
        if(memberRepository.existsByEmail(email.toLowerCase(Locale.ROOT))){
            throw new DuplicateMemberException("이미 가입된 이메일 입니다.");
        }
    }

    private void existsNickname(String nickname){
        if (memberRepository.existsByNickname(nickname)) {
            throw new DuplicateMemberException("이미 생성된 닉네임 입니다.");
        }
    }

}

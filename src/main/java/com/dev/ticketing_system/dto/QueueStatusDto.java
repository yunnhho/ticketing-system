package com.dev.ticketing_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class QueueStatusDto {
    private Long rank;            // 내 현재 순위
    private Long estimatedSeconds; // 예상 대기 시간 (초)
    private boolean isPass;       // 입장 가능 여부 (true면 통과)
}
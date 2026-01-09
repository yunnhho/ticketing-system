# 🎫 대용량 트래픽 대응 티켓 예매 시스템

Redis와 Kafka를 활용하여 수만 명의 동시 접속자가 발생하는 티켓팅 상황을 설계하고 구현한 프로젝트입니다.

## 🚀 Key Features
- **대기열 시스템**: Redis의 Sorted Set을 활용하여 순차적인 입장 처리 (초당 입장량 조절 가능)
- **분산 락 (Distributed Lock)**: Redisson을 사용하여 좌석 선점 시 발생하는 동시성 이슈 해결
- **비동기 결제 처리**: Kafka 이벤트를 발행하여 결제 프로세스를 비동기로 처리, 시스템 부하 분산
- **데이터 정합성 보장**: DB 낙관적 락(@Version)을 도입하여 Redis 락 이후의 2차 무결성 검증

## 🛠 Tech Stack
- **Backend**: Java 21, Spring Boot 3.4
- **Database**: MySQL 8.0
- **Cache/Concurrency**: Redis (Redisson)
- **Messaging**: Apache Kafka
- **Infrastructure**: Docker, Docker Compose

## 🏗 System Architecture
1. **대기열 진입**: 유저 접속 시 Redis Queue(ZSET)에 등록 및 대기 순번 확인
2. **입장 허용**: Scheduler가 주기적으로 상위 N명의 유저를 Active 상태(TTL 적용)로 전환
3. **좌석 선점**: Redisson을 이용해 5분간 좌석 임시 점유 (분산 락 적용)
4. **결제 완료**: Kafka 메시지 발행 → Consumer가 수신하여 DB 상태 변경(SOLD) 및 락 해제

## 🚦 How to Run
```bash
# 1. 저장소 클론
git clone [repository-url]

# 2. Docker Compose 실행
docker-compose up -d --build
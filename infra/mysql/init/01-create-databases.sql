-- 서비스별 DB를 분리한다. 컨테이너 최초 기동(빈 볼륨) 때 한 번 실행된다.
-- auth-service: 단말 공개키 등록 정보
create database if not exists aegis_auth
    character set utf8mb4
    collate utf8mb4_0900_ai_ci;

grant all privileges on aegis_auth.* to 'aegis'@'%';

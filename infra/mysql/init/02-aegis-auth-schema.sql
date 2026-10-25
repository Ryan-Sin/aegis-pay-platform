use aegis_auth;

create table if not exists terminal (
    terminal_id    varchar(64)  not null,
    store_id       varchar(64)  not null,
    key_algorithm  varchar(16)  not null comment 'EC_P256',
    public_key     varchar(512) not null comment 'X.509 SubjectPublicKeyInfo DER, base64',
    scopes         varchar(255) not null default '' comment 'JWT scopes claim, comma separated',
    status         varchar(16)  not null comment 'ACTIVE, SUSPENDED, REVOKED',
    created_at     datetime(6)  not null,
    updated_at     datetime(6)  not null,
    primary key (terminal_id),
    key idx_terminal_store_id (store_id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci;

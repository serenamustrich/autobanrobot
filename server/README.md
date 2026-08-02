# AutoBanRobot Server

Spring Boot JAR for receiving confirmed Ban events and serving a live dashboard.

## Local run

```bash
mvn -f server/pom.xml clean package
java -jar server/target/autoban-server-1.2.5.jar
```

The server requires MySQL configuration and intentionally has no embedded
fallback database. Open <http://127.0.0.1:59999> after it starts.

## MySQL configuration

Do not commit credentials. Supply them through environment variables:

```bash
export AUTOBAN_DB_URL='jdbc:mysql://127.0.0.1:3306/autoban?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export AUTOBAN_DB_USERNAME='autoban'
export AUTOBAN_DB_PASSWORD='replace-me'
java -jar server/target/autoban-server-1.2.5.jar
```

The database schema is created or updated automatically by Hibernate.
MySQL connections automatically use Hibernate's legacy dialect so the supplied
MySQL 5.7 database remains supported.

## API

- `POST /api/bans`: receive an idempotent confirmed Ban event.
- `GET /api/bans`: paginated list; supports `page`, `size`, and `query`.
- `GET /api/bans/stats`: total and today counts.
- `GET /api/bans/stream`: Server-Sent Events stream for the dashboard.
- `GET /api/keywords`: keyword hit ranking.
- `GET /api/mentions`: accounts mentioned by confirmed spam content.
- `GET /api/popular-terms`: exact terms offered to plugins for synchronization.
- `GET /api/rules`: public versioned detection-rule configuration used by plugins.
- `PUT /api/rules`: replace the online rule list and increment its version; requires
  `X-AutoBan-Admin-Token` matching `AUTOBAN_RULE_ADMIN_TOKEN`.
- `POST /api/clients/heartbeat`: record one anonymous plugin installation heartbeat.
- `GET /api/clients/stats`: online and cumulative anonymous plugin users.

Set a long random `AUTOBAN_RULE_ADMIN_TOKEN` in the deployment environment. The
token is never stored in MySQL or returned by the API. Rule configuration is
stored in the MySQL 5.7-compatible `rule_config` table as `MEDIUMTEXT`.

Rule fields include `scope` (`content`, `username`, or `displayName`), the
optional `requiresDefaultAvatar` guard, and either a validated regular-expression
`pattern` or a supported `matcher`. The browser executes only validated
regular expressions; the server never distributes executable JavaScript.

The dashboard supports Chinese, English, Spanish, Japanese, Korean, German,
French, Russian, and Italian. A client counts as online when its latest
heartbeat was received within two minutes.

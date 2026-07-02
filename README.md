# innovatech-ms-tickets

## Estado de evidencia

| Categoria | Estado |
| --- | --- |
| Implementado | CRUD de tickets, productor RabbitMQ, JWT, Actuator |
| Configurado | MySQL, RabbitMQ, perfiles `local/aws`, Docker |
| Validado | compilacion |
| Pendiente runtime | publicacion real de eventos y despliegue AWS |
| No evidenciado | consumidor oficial de `Ticket_Creado` fuera de la topologia documentada |

## 1. Descripcion general

Microservicio responsable de registrar y consultar tickets de soporte. Expone operaciones REST del dominio y publica un evento RabbitMQ al crear un ticket.

## 2. Rol dentro de la arquitectura

- API Gateway: entrada oficial para `/api/v1/tickets/**`.
- BFF: puede consumir datos para dashboards agregados.
- Persistencia: MySQL propia.
- RabbitMQ: productor de eventos.

Flujo base:

`Frontend -> API Gateway -> Tickets -> MySQL`

Flujo asincrono:

`Tickets -> RabbitMQ -> consumidor downstream`

## 3. Stack tecnico

- Java 21
- Spring Boot 3.5.14
- Maven Wrapper
- Spring Web
- Spring Data JPA
- Spring Security
- JWT
- Spring AMQP / RabbitMQ
- MySQL
- Spring Boot Actuator
- Springdoc OpenAPI
- Docker

## 4. Puerto y exposicion

| Item | Valor |
| --- | --- |
| Puerto interno | `8085` |
| Configuracion | `${SERVER_PORT:8085}` |
| Exposicion publica oficial | via API Gateway |
| Exposicion directa recomendada | no |

## 5. Perfiles soportados

| Perfil | Uso | Estado |
| --- | --- | --- |
| `local` | Docker local / desarrollo | Configurado |
| `aws` | ECS Fargate + RDS/RabbitMQ | Configurado |

Notas:

- El perfil por defecto es `local`.
- `ddl-auto` se mantiene en `update`.
- RabbitMQ queda documentado como configurado, no como validado extremo a extremo.

## 6. Variables de entorno requeridas

### Comunes

| Variable | Uso |
| --- | --- |
| `SERVER_PORT` | puerto HTTP |
| `JWT_SECRET` | secreto JWT |
| `APP_SECURITY_DOCS_PUBLIC` | docs publicas en local |

### Base de datos

| Variable | Local | AWS |
| --- | --- | --- |
| `DB_HOST` | opcional, default `localhost` | requerida |
| `DB_PORT` | opcional, default `3306` | requerida |
| `DB_NAME` | opcional, default `innovatech_tickets` | requerida |
| `DB_USERNAME` | opcional | requerida |
| `DB_PASSWORD` | opcional | requerida |

Compatibilidad adicional:

- `TICKETS_MYSQL_HOST`
- `TICKETS_MYSQL_PORT`
- `TICKETS_MYSQL_DATABASE`
- `TICKETS_MYSQL_USERNAME`
- `TICKETS_MYSQL_PASSWORD`

### RabbitMQ

| Variable | Uso |
| --- | --- |
| `RABBITMQ_HOST` | host broker |
| `RABBITMQ_PORT` | puerto broker |
| `RABBITMQ_USERNAME` | usuario broker |
| `RABBITMQ_PASSWORD` | password broker |

## 7. Endpoints principales

| Metodo | Ruta | Uso |
| --- | --- | --- |
| `POST` | `/api/v1/tickets` | crear ticket |
| `GET` | `/api/v1/tickets` | listar tickets |
| `GET` | `/api/v1/tickets/{id}` | obtener ticket |
| `PUT/PATCH` | `/api/v1/tickets/**` | actualizaciones del dominio |
| `GET` | `/actuator/health` | healthcheck |

## 8. Integracion y dependencias

| Componente | Tipo | Estado |
| --- | --- | --- |
| API Gateway | HTTP entrante | Evidenciado |
| BFF | HTTP interno | Evidenciado por configuracion |
| MySQL | persistencia | Evidenciado |
| RabbitMQ | publicacion AMQP | Evidenciado por configuracion y codigo |

## 9. Docker y build

- `Dockerfile` presente y validado.
- Imagen preparada para `SPRING_PROFILES_ACTIVE=aws` por defecto en contenedor.
- `docker-compose.yml` local inyecta `DB_*` y `RABBITMQ_*` con perfil `local`.

Comandos utiles:

```bash
./mvnw.cmd -q -DskipTests compile
docker build -t innovatech-tickets .
```

## 10. Estado actual de validacion

- `Validado`: compilacion.
- `Configurado`: perfiles `local/aws`, RabbitMQ, Docker, variables `DB_*`.
- `Pendiente runtime`: publicacion real del evento `Ticket_Creado` y validacion completa en AWS.

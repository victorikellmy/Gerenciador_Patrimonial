# =============================================================================
# Multi-stage build do Gerenciador Patrimonial.
#
# Stage 1: build
#   - Cacheia o download de dependências copiando antes apenas os arquivos do Gradle.
#   - Gera o jar reproduzível com `bootJar`.
#
# Stage 2: runtime
#   - Imagem JRE-only (menor superfície de ataque).
#   - Usuário não-root.
#   - O diretório /uploads é o ponto de montagem do volume NFS em produção
#     (definido pelo docker-compose).
# =============================================================================

# ----- STAGE 1: build -----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Cache de dependências
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Código fonte
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ----- STAGE 2: runtime -----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuário não-root para reduzir superfície de ataque
RUN addgroup -S app && adduser -S -G app app

# Diretório onde os anexos serão gravados (montado via NFS no compose)
RUN mkdir -p /uploads && chown app:app /uploads

COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown app:app app.jar

USER app

EXPOSE 8080
ENV JAVA_OPTS=""
ENV SPRING_PROFILES_ACTIVE=prod
ENV APP_STORAGE_PASTA_RAIZ=/uploads

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

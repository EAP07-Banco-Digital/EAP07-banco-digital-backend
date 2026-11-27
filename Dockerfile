# Etapa 1: Construcción (Build)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copiamos el archivo de configuración de dependencias
COPY pom.xml .
# Descargamos las dependencias para aprovechar el caché de Docker
RUN mvn dependency:go-offline

# Copiamos el resto del código fuente
COPY src ./src

# Compilamos el proyecto omitiendo los tests para acelerar el despliegue
RUN mvn clean package -DskipTests

# Etapa 2: Ejecución (Run)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiamos el archivo .jar generado en la etapa anterior
COPY --from=build /app/target/banco-digital-backend-0.0.1-SNAPSHOT.jar app.jar

# Exponemos el puerto (por convención, aunque Render inyectará el suyo)
EXPOSE 8080
# Puerto de gestion (Actuator/Prometheus); no se publica fuera de la red interna
EXPOSE 8081

# Comando para iniciar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]

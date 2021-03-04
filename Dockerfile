FROM maven:3.6.1-jdk-8-alpine AS MAVEN_BUILD

# copy the pom and src code to the container
#COPY ./ ./

# package our application code
#RUN mvn clean package

#RUN ls /

# the second stage of our build will use open jdk 8 on alpine 3.9
FROM openjdk:8-jre-alpine3.9

#COPY --from=MAVEN_BUILD ./target/*.jar app/grid-utils.jar
COPY ./target/*.jar app/grid-utils.jar
WORKDIR /app

# copy only the artifacts we need from the first stage and discard the rest

# set the startup command to execute the jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","grid-utils.jar"]
# 1st stage, build the app
FROM maven:3.6-jdk-8 as build

WORKDIR /fn

# Create a first layer to cache the "Maven World" in the local repository.
# Incremental docker builds will always resume after that, unless you update
# the pom
ADD pom.xml .
RUN mvn package -DskipTests

## which module do you like to kick, netty or reactor-netty? 
#ENV mainClass org.example.netty.FnServer
#ENV mainClass org.example.reactor.FnServer

ARG mainClass
ENV mainClass ${mainClass}

# Do the Maven build!
# Incremental docker builds will resume here when you change sources
ADD src src
RUN mvn package -DskipTests

RUN $JAVA_HOME/bin/jar xvf target/libs/netty-transport-native-epoll-*.jar META-INF/native/

RUN echo "done!"

# 2nd stage, build the runtime image
FROM openjdk:8-jre

WORKDIR /fn

# Copy the binary built in the 1st stage
COPY --from=build /fn/target/fn-netty.jar ./
COPY --from=build /fn/target/libs ./libs

# place native library
COPY --from=build /fn/META-INF/native ./native

CMD ["java", "-Djava.library.path=/fn/native", "-jar", "fn-netty.jar"]



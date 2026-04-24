FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . /app

RUN cd backend \
    && java -cp javacc.jar org.javacc.parser.Main LughatDad.jj \
    && javac -encoding UTF-8 *.java

EXPOSE 10000

CMD ["sh", "-c", "cd backend && java -cp . LughatDadServer"]

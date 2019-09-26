#!/bin/sh
docker run -it --rm --name items-extractor -v "$(pwd)":/app -v ~/.m2:/root/.m2 -w /app maven:3.3-jdk-8 mvn -e clean package

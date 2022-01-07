ItemsExtractor
==============

# To Build a Release (on Linux)
1. Install docker (see https://docs.docker.com/install/linux/docker-ce/ubuntu/ or find instructions for your distribution)

2. Build the package:
    1. `git clone https://github.com/Budabot/ItemsExtractor.git`
    2. `cd ItemsExtractor`
    3. `docker run -it --rm --name items-extractor-package -v "$(pwd)":/app -v ~/.m2:/root/.m2 -w /app maven:3.3-jdk-8 mvn -e clean package`
    
3. Release will be located at: `~/ItemsExtractor/target/release/ItemsExtractor-<version>`
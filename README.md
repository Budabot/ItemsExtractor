ItemsExtractor
==============

# To Build a Release (on Linux)
1. Install docker (see https://docs.docker.com/install/linux/docker-ce/ubuntu/ or find instructions for your distribution)

1. Install the scala-util library to your local m2 repository:
    1. `cd ~`
    1. `git clone https://github.com/bigwheels16/scala-util.git`
    1. `cd scala-util`
    1. `docker run -it --rm --name scala-util -v "$(pwd)":/app -v ~/.m2:/root/.m2 -w /app maven:3.3-jdk-8 mvn -e install`

1. Build a release:
    1. `./package.sh` (you may need to use `sudo` in from of this command depending on how your docker is configured)
    
1. Release will be located at: `~/ItemsExtractor/target/release/ItemsExtractor-<version>`
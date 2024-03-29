#!/bin/bash
MIN_VERSION=21
CUR_VERSION=`java -version 2>&1 | grep 'version' 2>&1 | awk -F\" '{ split($2,a,"."); print a[1]}'`

if [[ "$CUR_VERSION" ]]; then
    echo "Detected java version $CUR_VERSION."
    if [[ "$CUR_VERSION" -lt "$MIN_VERSION" ]]; then
        echo "Required java version is $MIN_VERSION. Exit."
        exit
    fi
else
    echo "Missing java. Exit"
    exit
fi

PROFILE=$1
if [[ $# == 0 ]]; then
    PROFILE="default"
fi

PWD=`pwd`
if [ ! -d "${PWD}/application/target" ]; then
  ./build.sh
fi



DB="${PWD}/data/db"
# Run the application
echo "Running Spring Boot application with profile $PROFILE, using $DB..."
export JDBC_URL="jdbc:h2:file:${DB}"
./mvnw spring-boot:run -pl application  -Dspring-boot.run.profiles=$PROFILE
